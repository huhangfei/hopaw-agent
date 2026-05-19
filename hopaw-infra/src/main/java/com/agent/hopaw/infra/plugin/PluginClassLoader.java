package com.agent.hopaw.infra.plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

public class PluginClassLoader extends ClassLoader {

    private static final Logger logger = LoggerFactory.getLogger(PluginClassLoader.class);

    private final File jarFile;
    private final String jarFileName;
    private final Map<String, byte[]> classCache = new HashMap<>();
    private final Map<String, byte[]> resourceCache = new HashMap<>();
    private final Map<String, File> extractedDirs = new HashMap<>();
    private JarFile cachedJarFile;

    public PluginClassLoader(File jarFile) throws IOException {
        super(PluginClassLoader.class.getClassLoader());
        this.jarFile = jarFile;
        this.jarFileName = jarFile.getName();
        this.cachedJarFile = new JarFile(jarFile);

        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();
                
                // 支持标准 JAR 和 Spring Boot FAT JAR 格式
                if (entryName.endsWith(".class") && !entryName.contains("module-info")) {
                    // 处理 BOOT-INF/classes/ 下的类(Spring Boot FAT JAR)
                    String className;
                    if (entryName.startsWith("BOOT-INF/classes/")) {
                        className = entryName.substring("BOOT-INF/classes/".length())
                                .replace('/', '.')
                                .replace(".class", "");
                    } else {
                        className = entryName.replace('/', '.').replace(".class", "");
                    }
                    
                    byte[] bytes = readAllBytes(jar.getInputStream(entry));
                    classCache.put(className, bytes);
                } else if (!entryName.endsWith(".class") && !entry.isDirectory()) {
                    // 缓存资源文件（如 Playwright 驱动等）
                    byte[] bytes = readAllBytes(jar.getInputStream(entry));
                    resourceCache.put(entryName, bytes);
                }
            }
        }
        logger.info("Preloaded {} classes and {} resources from {}", classCache.size(), resourceCache.size(), jarFileName);
    }

    public List<String> scanAgentToolClasses() {
        List<String> result = new ArrayList<>();
        for (String className : classCache.keySet()) {
            try {
                Class<?> clazz = loadClass(className);
                if (com.agent.hopaw.infra.tool.AgentTool.class.isAssignableFrom(clazz)
                        && !clazz.isInterface() && !java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
                    result.add(className);
                    logger.info("Found AgentTool plugin class: {} in {}", className, jarFileName);
                }
            } catch (ClassNotFoundException | NoClassDefFoundError | UnsupportedClassVersionError e) {
                // 提升日志级别到 WARN,方便排查插件加载失败原因
                //logger.warn("Skip class {} during scan: {} - {}", className, e.getClass().getSimpleName(), e.getMessage());
            } catch (Throwable e) {
                // 捕获所有其他异常(如 ExceptionInInitializerError 等)
                logger.error("Unexpected error scanning class {}: {} - {}", className, e.getClass().getSimpleName(), e.getMessage(), e);
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

    @Override
    public URL getResource(String name) {
        if (resourceCache.containsKey(name)) {
            try {
                File tempFile = File.createTempFile("plugin-resource-", "-" + name.replace('/', '-'));
                tempFile.deleteOnExit();
                Files.write(tempFile.toPath(), resourceCache.get(name));
                return tempFile.toURI().toURL();
            } catch (IOException e) {
                logger.warn("Failed to create temp file for resource: {}", name, e);
            }
        }

        String dirPrefix = name.endsWith("/") ? name : name + "/";
        for (String key : resourceCache.keySet()) {
            if (key.startsWith(dirPrefix)) {
                File cached = extractedDirs.get(name);
                if (cached != null && cached.exists()) {
                    try {
                        return cached.toURI().toURL();
                    } catch (IOException e) {
                        logger.warn("Failed to get URL for cached dir: {}", name, e);
                    }
                }

                try {
                    Path tempDir = Files.createTempDirectory("plugin-res-dir-");
                    for (Map.Entry<String, byte[]> entry : resourceCache.entrySet()) {
                        if (entry.getKey().startsWith(dirPrefix)) {
                            String relPath = entry.getKey().substring(dirPrefix.length());
                            File targetFile = new File(tempDir.toFile(), relPath);
                            targetFile.getParentFile().mkdirs();
                            Files.write(targetFile.toPath(), entry.getValue());
                            targetFile.deleteOnExit();
                        }
                    }
                    File tempDirFile = tempDir.toFile();
                    tempDirFile.deleteOnExit();
                    extractedDirs.put(name, tempDirFile);
                    logger.debug("Extracted directory resource '{}' ({} files) to {}", name,
                            resourceCache.keySet().stream().filter(k -> k.startsWith(dirPrefix)).count(), tempDir);
                    return tempDirFile.toURI().toURL();
                } catch (IOException e) {
                    logger.warn("Failed to extract directory resource: {}", name, e);
                }
                break;
            }
        }

        return super.getResource(name);
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        // 先从缓存中查找资源
        if (resourceCache.containsKey(name)) {
            return new java.io.ByteArrayInputStream(resourceCache.get(name));
        }
        // 委托给父类加载器
        return super.getResourceAsStream(name);
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        byte[] buf = new byte[4096];
        int total = 0;
        int capacity = 4096;
        byte[] result = new byte[capacity];
        int n;
        while ((n = in.read(buf)) > 0) {
            if (total + n > capacity) {
                capacity = Math.max(capacity * 2, total + n);
                byte[] newResult = new byte[capacity];
                System.arraycopy(result, 0, newResult, 0, total);
                result = newResult;
            }
            System.arraycopy(buf, 0, result, total, n);
            total += n;
        }
        if (total < capacity) {
            byte[] trimmed = new byte[total];
            System.arraycopy(result, 0, trimmed, 0, total);
            return trimmed;
        }
        return result;
    }
}
