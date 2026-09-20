package com.agent.hopaw.infra.plugin;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.agent.hopaw.infra.tool.AgentTool;

/**
 * 插件加载器：以 {@link AgentPlugin} 为主体加载插件 JAR。
 *
 * <p>加载契约（一个 JAR = 一个 AgentPlugin）：</p>
 * <ol>
 *   <li>扫描 JAR 内的 AgentPlugin 实现，未提供则拒绝安装（纯前端插件也应提供空工具的 AgentPlugin）；</li>
 *   <li>实例化插件 → autowireBean → 插件 {@code asyncInit()}（构建插件级共享资源）；</li>
 *   <li>调用插件 {@code getTools()} 取 0..N 个工具实例 → 逐个 autowireBean → 逐个 {@code asyncInit()}；</li>
 *   <li>以 pluginId 为键注册；同 pluginId 已存在则拒绝（升级走覆盖安装）。</li>
 * </ol>
 *
 * <p>卸载契约（逆序）：逐工具 {@code destroy()} → 插件 {@code destroy()} → 关闭 classloader。</p>
 */
@Component
public class JarPluginLoader {

    private static final Logger logger = LoggerFactory.getLogger(JarPluginLoader.class);

    private final PluginRegistry registry;
    private final AutowireCapableBeanFactory beanFactory;
    private final Path pluginDir;

    /** shutdown 标志，防止 PreDestroy 之后仍有 loadPlugin 在后台注册插件。 */
    private volatile boolean shuttingDown = false;

    public JarPluginLoader(PluginRegistry registry,
                           AutowireCapableBeanFactory beanFactory,
                           @Value("${agent.plugin.dir:plugins}") String pluginDirPath) {
        this.registry = registry;
        this.beanFactory = beanFactory;
        this.pluginDir = new File(pluginDirPath).toPath().toAbsolutePath().normalize();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        try {
            File dir = pluginDir.toFile();
            if (!dir.exists()) {
                if (dir.mkdirs()) {
                    logger.info("Created plugin directory: {}", pluginDir);
                } else {
                    logger.warn("Failed to create plugin directory: {}", pluginDir);
                    return;
                }
            }
            if (!dir.isDirectory()) {
                logger.error("Plugin path is not a directory: {}", pluginDir);
                return;
            }

            initialScan(dir);
        } catch (VirtualMachineError e) {
            // 虚拟内存类错误不应被吞掉，直接抛出让 JVM 处理
            logger.error("Fatal VM error during plugin initialization", e);
            throw e;
        } catch (Throwable ex) {
            // 捕获其他所有异常和错误（包括 NoClassDefFoundError、NoSuchMethodError 等）
            // 确保插件加载失败不会影响应用启动
            logger.error("Error during plugin initialization (non-fatal, application will continue)", ex);
        }
    }

    @PreDestroy
    public void shutdown() {
        shuttingDown = true;
        List<PluginRegistry.PluginEntry> entries = registry.getAllPluginEntries();
        for (PluginRegistry.PluginEntry entry : entries) {
            registry.unregister(entry.getPluginId());
        }
        logger.info("Plugin loader shutdown, all plugins unloaded");
    }

    private void initialScan(File dir) {
        File[] jars = dir.listFiles((f, name) -> name.endsWith(".jar"));
        if (jars != null) {
            // 按文件名排序，保证加载顺序确定（插件间如存在依赖，依赖方应排前）
            Arrays.sort(jars, Comparator.comparing(File::getName));
            for (File jar : jars) {
                loadPlugin(jar);
            }
        }
    }

    /**
     * 加载插件 JAR。
     *
     * @return 插件提供的工具数量（加载失败返回 0）
     */
    public int loadPlugin(File jarFile) {
        String jarName = jarFile.getName();
        if (registry.hasPluginJar(jarName)) {
            logger.debug("Plugin jar already loaded: {}", jarName);
            return 0;
        }
        if (shuttingDown) {
            logger.debug("Plugin loader is shutting down, skip loading: {}", jarName);
            return 0;
        }

        PluginClassLoader classLoader = null;
        try {
            classLoader = new PluginClassLoader(jarFile);

            List<String> pluginClassNames = classLoader.scanAgentPluginClasses();
            if (pluginClassNames.isEmpty()) {
                logger.error("Rejected plugin [{}]: 未找到 AgentPlugin 实现（插件 JAR 必须提供 AgentPlugin，"
                        + "纯前端插件也应提供 getTools() 返回空列表的 AgentPlugin）", jarName);
                classLoader.close();
                return 0;
            }
            if (pluginClassNames.size() > 1) {
                logger.error("Rejected plugin [{}]: 存在 {} 个 AgentPlugin 实现，一个插件 JAR 只能提供一个插件主体",
                        jarName, pluginClassNames.size());
                classLoader.close();
                return 0;
            }

            // 1) 实例化插件主体并注入 Spring 依赖
            Class<?> pluginClass = classLoader.loadClass(pluginClassNames.get(0));
            AgentPlugin plugin = (AgentPlugin) pluginClass.getDeclaredConstructor().newInstance();
            beanFactory.autowireBean(plugin);

            // 2) 同 pluginId 拒绝共存（升级必须走覆盖安装）
            if (registry.hasPlugin(plugin.getId())) {
                logger.error("Rejected plugin [{}]: pluginId [{}] 已被占用，请先卸载或走覆盖升级", jarName, plugin.getId());
                classLoader.close();
                return 0;
            }

            // 3) 插件级异步初始化（构建共享资源，供其创建的工具使用）
            plugin.asyncInit();

            // 4) 插件作为工具工厂分发工具实例，框架补充依赖注入与工具级初始化
            List<AgentTool> tools = new ArrayList<>();
            List<AgentTool> provided = plugin.getTools();
            if (provided != null) {
                for (AgentTool tool : provided) {
                    if (tool == null) {
                        continue;
                    }
                    try {
                        beanFactory.autowireBean(tool);
                        tool.asyncInit();
                        tools.add(tool);
                    } catch (Exception e) {
                        logger.error("Failed to initialize tool [{}] of plugin [{}]",
                                tool.getClass().getSimpleName(), plugin.getId(), e);
                    }
                }
            }

            // shutdown 复查：asyncInit 可能耗时较长，避免在 shutdown 之后注册新插件
            if (shuttingDown) {
                classLoader.close();
                logger.debug("Plugin loader is shutting down, skip registering: {}", jarName);
                return 0;
            }

            boolean registered = registry.register(plugin, jarName, classLoader, tools);
            if (!registered) {
                // 并发下同 pluginId 已被注册，丢弃本次 classloader，避免泄漏
                classLoader.close();
                logger.warn("Plugin [{}] was already registered concurrently, dropped redundant load", plugin.getId());
                return 0;
            }
            logger.info("Loaded plugin: {} [{}] with {} tools", plugin.getId(), jarName, tools.size());
            return tools.size();
        } catch (VirtualMachineError e) {
            closeQuietly(classLoader);
            throw e;
        } catch (Throwable e) {
            closeQuietly(classLoader);
            logger.error("Failed to load plugin: {}", jarName, e);
            return 0;
        }
    }

    /**
     * 扫描 JAR 内的插件信息（不注册、不初始化），用于安装前的校验与冲突检测。
     *
     * <p>会实例化 AgentPlugin 以读取元数据，因此插件构造器应保持无副作用。</p>
     */
    public PluginScanResult scanPluginInfo(File jarFile) {
        String jarName = jarFile.getName();
        PluginScanResult result = new PluginScanResult();
        result.jarFileName = jarName;

        try (PluginClassLoader classLoader = new PluginClassLoader(jarFile)) {
            List<String> pluginClassNames = classLoader.scanAgentPluginClasses();
            if (pluginClassNames.isEmpty()) {
                result.errorMessage = "未找到 AgentPlugin 实现（插件 JAR 必须提供 AgentPlugin）";
                return result;
            }
            if (pluginClassNames.size() > 1) {
                result.errorMessage = "存在多个 AgentPlugin 实现，一个插件 JAR 只能提供一个插件主体";
                return result;
            }

            Class<?> pluginClass = classLoader.loadClass(pluginClassNames.get(0));
            AgentPlugin plugin = (AgentPlugin) pluginClass.getDeclaredConstructor().newInstance();
            result.pluginId = plugin.getId();
            result.pluginName = plugin.getName();
            result.pluginVersion = plugin.getVersion();

            // 反射收集 JAR 内所有 AgentTool 实现类声明的 @Tool 方法名（不实例化工具，避免副作用）
            List<String> toolNames = new ArrayList<>();
            for (String className : classLoader.scanAgentToolClasses()) {
                try {
                    Class<?> clazz = classLoader.loadClass(className);
                    for (java.lang.reflect.Method method : clazz.getDeclaredMethods()) {
                        dev.langchain4j.agent.tool.Tool toolAnn = method.getAnnotation(dev.langchain4j.agent.tool.Tool.class);
                        if (toolAnn != null) {
                            String toolName = toolAnn.name();
                            if (toolName.isEmpty()) {
                                toolName = method.getName();
                            }
                            toolNames.add(toolName);
                        }
                    }
                } catch (Exception e) {
                    logger.debug("Failed to scan tool class: {}", className, e);
                }
            }
            result.toolNames = toolNames;
        } catch (Throwable e) {
            result.errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
        }
        return result;
    }

    public static class PluginScanResult {
        public String jarFileName;
        /** 插件标识 */
        public String pluginId;
        /** 插件显示名称 */
        public String pluginName;
        public String pluginVersion;
        public List<String> toolNames;
        public String errorMessage;

        public boolean hasError() {
            return errorMessage != null;
        }
    }

    /**
     * 卸载插件（按 JAR 文件名定位，注销、销毁工具与插件、关闭 classloader）。
     *
     * @return true 表示确实卸载了已注册的插件
     */
    private boolean unloadPlugin(String jarName) {
        PluginRegistry.PluginEntry removed = registry.unregisterByJarFileName(jarName);
        if (removed != null) {
            logger.info("Unloaded plugin: {} [{}] ({} tools released)",
                    removed.getPluginId(), jarName, removed.getTools().size());
            return true;
        }
        return false;
    }

    public boolean unloadAndDeletePlugin(String jarName) {
        // 路径穿越防护：先校验再执行卸载副作用，避免校验失败时插件已被卸载但 JAR 未删除
        Path resolved = pluginDir.resolve(jarName).normalize();
        if (!resolved.startsWith(pluginDir)) {
            logger.error("Rejected plugin path traversal attempt: {}", jarName);
            return false;
        }

        // 卸载插件，释放 JAR 文件句柄，避免 Windows 上删除失败
        if (!unloadPlugin(jarName)) {
            logger.warn("Plugin not found for unload: {}", jarName);
            return false;
        }

        File jarFile = resolved.toFile();
        if (jarFile.exists()) {
            if (jarFile.delete()) {
                logger.info("Deleted plugin JAR: {}", jarFile.getAbsolutePath());
            } else {
                logger.warn("Failed to delete plugin JAR: {}", jarFile.getAbsolutePath());
            }
        }
        return true;
    }

    public Path getPluginDir() {
        return pluginDir;
    }

    private static void closeQuietly(PluginClassLoader classLoader) {
        if (classLoader != null) {
            try {
                classLoader.close();
            } catch (Exception e) {
                logger.warn("Failed to close plugin classloader after error", e);
            }
        }
    }
}
