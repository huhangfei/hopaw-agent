package com.agent.hopaw.infra.plugin;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 插件类加载器。
 *
 * <p>类加载策略：父优先（与标准 ClassLoader 一致）。若插件自带依赖库与宿主冲突
 * 时会出现父优先遮蔽问题，如需 child-first 请另行评估后调整 findClass/loadClass。</p>
 *
 * <p>资源缓存策略：仅将 class 字节与小于 {@value #MAX_RESOURCE_CACHE_BYTES} 的资源
 * 缓存进堆内存；大资源（如 Playwright/Chromium 驱动）延迟从始终保持打开的
 * {@link JarFile} 流式读取，避免两个插件就导致 OOM。</p>
 *
 * <p>实现 {@link Closeable}：关闭时显式释放 JAR 文件句柄并删除所有临时文件，
 * 以便 Windows 上能删除插件 JAR。</p>
 */
public class PluginClassLoader extends ClassLoader implements Closeable {

    private static final Logger logger = LoggerFactory.getLogger(PluginClassLoader.class);

    /** 超过该大小的资源不再缓存进堆内存，改为从 JAR 延迟流式读取。 */
    private static final long MAX_RESOURCE_CACHE_BYTES = 256 * 1024L;

    /** 临时文件名后缀的最大长度（截断时保留尾部以维持扩展名）。 */
    private static final int MAX_TEMP_SUFFIX_LENGTH = 64;

    private final File jarFile;
    private final String jarFileName;
    /** 始终保持打开的 JAR，用于流式读取大资源；close() 时关闭。 */
    private final JarFile jarHandle;
    private final ConcurrentMap<String, byte[]> classCache = new ConcurrentHashMap<>();
    /** 仅缓存小资源（小于 MAX_RESOURCE_CACHE_BYTES）。 */
    private final ConcurrentMap<String, byte[]> resourceCache = new ConcurrentHashMap<>();
    /** JAR 中所有非目录条目名（含大资源），便于延迟流式读取与目录抽取。 */
    private final Set<String> allEntryNames = ConcurrentHashMap.newKeySet();
    /** 资源名 -> 已落地临时文件（memoize，close() 时删除）。 */
    private final ConcurrentMap<String, File> resourceTempFiles = new ConcurrentHashMap<>();
    /** 目录资源名 -> 已抽取临时目录（memoize，close() 时删除）。 */
    private final ConcurrentMap<String, File> extractedDirs = new ConcurrentHashMap<>();
    /** 目录资源抽取互斥锁（避免并发重复抽取同一目录）。 */
    private final Object lock = new Object();
    private volatile boolean closed = false;

    public PluginClassLoader(File jarFile) throws IOException {
        super(PluginClassLoader.class.getClassLoader());
        this.jarFile = jarFile;
        this.jarFileName = jarFile.getName();

        JarFile jar = null;
        try {
            jar = new JarFile(jarFile);
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();

                // 支持标准 JAR 和 Spring Boot FAT JAR（仅 BOOT-INF/classes/ 部分，
                // 不处理 BOOT-INF/lib/ 下的嵌套 JAR）
                if (entryName.endsWith(".class") && !entryName.contains("module-info")) {
                    String className;
                    if (entryName.startsWith("BOOT-INF/classes/")) {
                        className = entryName.substring("BOOT-INF/classes/".length());
                    } else {
                        className = entryName;
                    }
                    // 先截掉结尾的 .class 后缀再做斜杠转换；
                    // 不能用 replace(".class","")，否则路径中 class 开头的目录段会被误删
                    // （如 corejs/classfile/Foo.class 曾被错误转为 corejsfile.Foo，导致运行时 NoClassDefFoundError）
                    className = className.substring(0, className.length() - ".class".length())
                            .replace('/', '.');

                    try (InputStream in = jar.getInputStream(entry)) {
                        classCache.put(className, in.readAllBytes());
                    }
                } else if (!entryName.endsWith(".class") && !entry.isDirectory()) {
                    allEntryNames.add(entryName);
                    // 只缓存小资源；大资源延迟流式读取，避免堆内存被自带的驱动类文件撑爆
                    if (entry.getSize() >= 0 && entry.getSize() <= MAX_RESOURCE_CACHE_BYTES) {
                        try (InputStream in = jar.getInputStream(entry)) {
                            resourceCache.put(entryName, in.readAllBytes());
                        }
                    }
                }
            }
            this.jarHandle = jar;
        } catch (Throwable t) {
            if (jar != null) {
                try {
                    jar.close();
                } catch (IOException ignore) {
                    // ignore
                }
            }
            throw t;
        }
        logger.info("Preloaded {} classes and {} small resources from {}", classCache.size(), resourceCache.size(), jarFileName);
    }

    public List<String> scanAgentToolClasses() {
        List<String> result = new java.util.ArrayList<>();
        for (String className : classCache.keySet()) {
            try {
                Class<?> clazz = loadClass(className);
                if (com.agent.hopaw.infra.tool.AgentTool.class.isAssignableFrom(clazz)
                        && !clazz.isInterface() && !java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
                    result.add(className);
                    logger.debug("Found AgentTool plugin class: {} in {}", className, jarFileName);
                }
            } catch (ClassNotFoundException | NoClassDefFoundError | UnsupportedClassVersionError e) {
                // 忽略无法加载的单类，不影响整体扫描
                logger.debug("Skip class {} during scan: {} - {}", className, e.getClass().getSimpleName(), e.getMessage());
            } catch (Throwable e) {
                logger.error("Unexpected error scanning class {}: {} - {}", className, e.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
        return result;
    }

    /**
     * 扫描 JAR 内所有 {@link com.agent.hopaw.infra.plugin.AgentPlugin} 实现类。
     *
     * <p>插件体系以 AgentPlugin 为主体：一个 JAR 应且仅应提供一个 AgentPlugin 实现，
     * 由它声明插件元数据并通过 getTools() 对外分发 0..N 个 AgentTool。</p>
     */
    public List<String> scanAgentPluginClasses() {
        List<String> result = new java.util.ArrayList<>();
        for (String className : classCache.keySet()) {
            try {
                Class<?> clazz = loadClass(className);
                if (com.agent.hopaw.infra.plugin.AgentPlugin.class.isAssignableFrom(clazz)
                        && !clazz.isInterface()
                        && !java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
                    result.add(className);
                    logger.debug("Found AgentPlugin class: {} in {}", className, jarFileName);
                }
            } catch (ClassNotFoundException | NoClassDefFoundError | UnsupportedClassVersionError e) {
                logger.debug("Skip class {} during plugin scan: {} - {}", className, e.getClass().getSimpleName(), e.getMessage());
            } catch (Throwable e) {
                logger.error("Unexpected error scanning plugin class {}: {} - {}", className, e.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
        return result;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] bytes = classCache.get(name);
        if (bytes != null) {
            try {
                return defineClass(name, bytes, 0, bytes.length, null);
            } catch (UnsupportedClassVersionError e) {
                logger.warn("Cannot load class {} (incompatible Java version): {}", name, e.getMessage());
                throw new ClassNotFoundException("Incompatible class version: " + name, e);
            }
        }
        throw new ClassNotFoundException(name);
    }

    public String getJarFileName() {
        return jarFileName;
    }

    /**
     * 获取插件 JAR 文件对象
     */
    public File getJarFile() {
        return jarFile;
    }

    /**
     * 仅在本 JAR 内解析资源 URL（小资源/大资源走临时文件、目录走延迟抽取），
     * 供 {@link #getResource(String)}、{@link #findResource(String)}、
     * {@link #findResources(String)} 复用。未命中返回 null。
     */
    private URL resolveResourceUrl(String name) {
        if (closed) {
            return null;
        }
        File temp = getOrCreateTempFile(name);
        if (temp != null) {
            return toUrl(temp);
        }
        // 目录资源：抽取前缀匹配的所有条目到一个临时目录
        File dir = getOrCreateExtractedDir(name);
        if (dir != null) {
            return toUrl(dir);
        }
        return null;
    }

    /**
     * 获取（或按需创建）资源对应的临时文件。注意：I/O 均在 ConcurrentHashMap
     * 锁外完成（先计算再 putIfAbsent），失败结果不缓存以便后续重试。
     */
    private File getOrCreateTempFile(String name) {
        File existing = resourceTempFiles.get(name);
        if (existing != null) {
            return existing;
        }
        if (!allEntryNames.contains(name)) {
            return null;
        }
        byte[] cached = resourceCache.get(name);
        File created;
        try {
            created = cached != null ? writeToTempFile(name, cached) : writeLargeToTempFile(name);
        } catch (IOException e) {
            logger.warn("Failed to create temp file for resource: {}", name, e);
            return null;
        }
        if (created == null) {
            return null;
        }
        File raced = resourceTempFiles.putIfAbsent(name, created);
        if (raced != null) {
            // 并发竞争失败，删除本次多余产物
            deleteQuietly(created);
            return raced;
        }
        return created;
    }

    /**
     * 获取（或按需抽取）目录资源对应的临时目录。抽取全程不持有任何 map 锁，
     * 仅通过内部互斥锁避免同一目录被并发重复抽取。
     */
    private File getOrCreateExtractedDir(String name) {
        if (!hasDirectoryPrefix(name)) {
            return null;
        }
        synchronized (lock) {
            File existing = extractedDirs.get(name);
            if (existing != null) {
                return existing;
            }
            File created = extractDirectory(name);
            if (created == null) {
                return null;
            }
            extractedDirs.put(name, created);
            return created;
        }
    }

    private boolean hasDirectoryPrefix(String name) {
        String prefix = name.endsWith("/") ? name : name + "/";
        for (String key : allEntryNames) {
            if (key.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** 为已缓存在堆中的小资源落地一个临时文件（memoize 避免重复创建 + deleteOnExit 泄漏）。 */
    private File writeToTempFile(String name, byte[] bytes) throws IOException {
        Path temp = createTempFileFor(name);
        Files.write(temp, bytes);
        return temp.toFile();
    }

    /** 将 JAR 中的大资源流式落地为临时文件。 */
    private File writeLargeToTempFile(String name) throws IOException {
        JarEntry entry = (JarEntry) jarHandle.getEntry(name);
        if (entry == null || entry.isDirectory()) {
            return null;
        }
        Path temp = createTempFileFor(name);
        try (InputStream in = jarHandle.getInputStream(entry)) {
            Files.copy(in, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return temp.toFile();
    }

    /**
     * 创建临时文件。后缀取自资源文件名（净化非法字符后保留扩展名），
     * 避免消费方依赖扩展名判断文件格式时失效。
     */
    private static Path createTempFileFor(String name) throws IOException {
        return Files.createTempFile("plugin-resource-", tempSuffixFor(name));
    }

    /** 从资源名提取合法的临时文件后缀：仅保留字母数字、点、下划线、连字符。 */
    private static String tempSuffixFor(String name) {
        String base = name.substring(name.lastIndexOf('/') + 1);
        base = base.replaceAll("[^\\w.\\-]", "_");
        // 去掉首尾点号，避免 Windows 下文件名以点开头/结尾被系统静默处理
        base = base.replaceAll("^\\.+", "").replaceAll("\\.+$", "");
        if (base.isEmpty()) {
            return ".tmp";
        }
        if (base.length() > MAX_TEMP_SUFFIX_LENGTH) {
            // 截断保留尾部以维持扩展名
            base = base.substring(base.length() - MAX_TEMP_SUFFIX_LENGTH);
        }
        return base;
    }

    /** 将 JAR 中给定目录前缀下的所有条目抽取到一个临时目录（调用方负责同步与缓存）。 */
    private File extractDirectory(String name) {
        String prefix = name.endsWith("/") ? name : name + "/";
        try {
            Path tempDir = Files.createTempDirectory("plugin-res-dir-");
            int count = 0;
            for (String key : allEntryNames) {
                if (!key.startsWith(prefix)) {
                    continue;
                }
                String relPath = key.substring(prefix.length());
                File targetFile = new File(tempDir.toFile(), relPath);
                targetFile.getParentFile().mkdirs();
                byte[] cached = resourceCache.get(key);
                if (cached != null) {
                    Files.write(targetFile.toPath(), cached);
                } else {
                    JarEntry entry = (JarEntry) jarHandle.getEntry(key);
                    if (entry != null && !entry.isDirectory()) {
                        try (InputStream in = jarHandle.getInputStream(entry)) {
                            Files.copy(in, targetFile.toPath(),
                                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
                count++;
            }
            if (count == 0) {
                Files.deleteIfExists(tempDir);
                return null;
            }
            logger.debug("Extracted directory resource '{}' ({} files) to {}", name, count, tempDir);
            return tempDir.toFile();
        } catch (IOException e) {
            logger.warn("Failed to extract directory resource: {}", name, e);
            return null;
        }
    }

    private static URL toUrl(File f) {
        try {
            return f.toURI().toURL();
        } catch (IOException e) {
            logger.warn("Failed to get URL for {}", f, e);
            return null;
        }
    }

    @Override
    public URL getResource(String name) {
        URL url = resolveResourceUrl(name);
        if (url != null) {
            return url;
        }
        return super.getResource(name);
    }

    @Override
    protected URL findResource(String name) {
        return resolveResourceUrl(name);
    }

    @Override
    protected Enumeration<URL> findResources(String name) throws IOException {
        URL url = resolveResourceUrl(name);
        if (url != null) {
            return Collections.enumeration(Collections.singletonList(url));
        }
        return Collections.emptyEnumeration();
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        if (!closed) {
            // 先从缓存中读取小资源
            byte[] cached = resourceCache.get(name);
            if (cached != null) {
                return new ByteArrayInputStream(cached);
            }
            // 大资源：从 JAR 流式读取，不占用堆内存
            if (allEntryNames.contains(name)) {
                try {
                    JarEntry entry = (JarEntry) jarHandle.getEntry(name);
                    if (entry != null && !entry.isDirectory()) {
                        return jarHandle.getInputStream(entry);
                    }
                } catch (IOException e) {
                    logger.warn("Failed to read large resource: {}", name, e);
                }
            }
        }
        // 委托给父类加载器（close 后也走此路径，避免操作已关闭的 JarFile 抛异常）
        return super.getResourceAsStream(name);
    }

    @Override
    public void close() {
        // 幂等保护：重复 close 直接返回
        if (closed) {
            return;
        }
        closed = true;
        for (File f : resourceTempFiles.values()) {
            deleteQuietly(f);
        }
        resourceTempFiles.clear();
        for (File dir : extractedDirs.values()) {
            deleteRecursively(dir);
        }
        extractedDirs.clear();
        resourceCache.clear();
        classCache.clear();
        try {
            jarHandle.close();
        } catch (IOException e) {
            logger.warn("Failed to close jar handle for {}: {}", jarFileName, e.getMessage());
        }
        logger.info("Closed plugin classloader for {}", jarFileName);
    }

    private static void deleteQuietly(File f) {
        if (f != null && f.exists()) {
            try {
                Files.deleteIfExists(f.toPath());
            } catch (IOException e) {
                logger.debug("Failed to delete temp file {}: {}", f, e.getMessage());
            }
        }
    }

    private static void deleteRecursively(File dir) {
        if (dir == null || !dir.exists()) {
            return;
        }
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    deleteRecursively(child);
                } else {
                    deleteQuietly(child);
                }
            }
        }
        deleteQuietly(dir);
    }
}