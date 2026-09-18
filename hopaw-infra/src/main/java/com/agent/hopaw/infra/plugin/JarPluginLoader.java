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

@Component
public class JarPluginLoader {

    private static final Logger logger = LoggerFactory.getLogger(JarPluginLoader.class);

    private final DynamicToolRegistry registry;
    private final AutowireCapableBeanFactory beanFactory;
    private final Path pluginDir;

    /** shutdown 标志，防止 PreDestroy 之后仍有 loadPlugin 在后台注册插件。 */
    private volatile boolean shuttingDown = false;

    public JarPluginLoader(DynamicToolRegistry registry,
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
        for (String jarName : registry.getPluginNames()) {
            registry.unregister(jarName);
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

    public int loadPlugin(File jarFile) {
        String jarName = jarFile.getName();
        if (registry.hasPlugin(jarName)) {
            logger.debug("Plugin already loaded: {}", jarName);
            return 0;
        }
        if (shuttingDown) {
            logger.debug("Plugin loader is shutting down, skip loading: {}", jarName);
            return 0;
        }

        PluginClassLoader classLoader = null;
        try {
            classLoader = new PluginClassLoader(jarFile);
            List<String> classNames = classLoader.scanAgentToolClasses();

            if (classNames.isEmpty()) {
                logger.warn("No AgentTool implementation found in: {}", jarName);
                classLoader.close();
                return 0;
            }

            List<AgentTool> tools = new ArrayList<>();
            for (String className : classNames) {
                try {
                    Class<?> clazz = classLoader.loadClass(className);
                    AgentTool tool = (AgentTool) clazz.getDeclaredConstructor().newInstance();
                    beanFactory.autowireBean(tool);
                    tool.asyncInit();
                    tools.add(tool);
                } catch (Exception e) {
                    logger.error("Failed to load tool class: {}", className, e);
                }
            }

            if (tools.isEmpty()) {
                logger.warn("No tool could be instantiated in plugin: {} (all {} classes failed)", jarName, classNames.size());
                classLoader.close();
                return 0;
            }

            // shutdown 复查：asyncInit 可能耗时较长，避免在 shutdown 之后注册新插件
            if (shuttingDown) {
                classLoader.close();
                logger.debug("Plugin loader is shutting down, skip registering: {}", jarName);
                return 0;
            }

            boolean registered = registry.register(jarName, classLoader, tools);
            if (!registered) {
                // 并发下同名插件已被注册，丢弃本次 classloader，避免泄漏
                classLoader.close();
                logger.warn("Plugin [{}] was already registered concurrently, dropped redundant load", jarName);
                return 0;
            }
            logger.info("Loaded plugin: {} with {} tools", jarName, tools.size());
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

    public PluginScanResult scanPluginInfo(File jarFile) {
        String jarName = jarFile.getName();
        PluginScanResult result = new PluginScanResult();
        result.jarFileName = jarName;

        try (PluginClassLoader classLoader = new PluginClassLoader(jarFile)) {
            List<String> classNames = classLoader.scanAgentToolClasses();

            if (classNames.isEmpty()) {
                result.errorMessage = "No AgentTool implementation found";
                return result;
            }

            // 遍历所有 AgentTool 类，聚合 JAR 内全部工具方法（不再只报告第一个类）
            List<String> toolNames = new ArrayList<>();
            for (String className : classNames) {
                try {
                    Class<?> clazz = classLoader.loadClass(className);
                    // 仅在尚未取得插件名时实例化一次（构造器可能有副作用，避免多余实例化）；
                    // 其余类仅通过反射收集方法，无需实例化
                    if (result.pluginName == null) {
                        AgentTool tool = (AgentTool) clazz.getDeclaredConstructor().newInstance();
                        result.pluginName = tool.getName();
                    }
                    // 使用 getDeclaredMethods 仅收集本类声明的 @Tool 方法，避免误收继承方法
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
            if (result.pluginName == null) {
                result.errorMessage = "No usable AgentTool class found";
            } else {
                result.toolNames = toolNames;
            }
        } catch (Throwable e) {
            result.errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
        }
        return result;
    }

    public static class PluginScanResult {
        public String jarFileName;
        public String pluginName;
        public List<String> toolNames;
        public String errorMessage;

        public boolean hasError() {
            return errorMessage != null;
        }
    }

    /**
     * 卸载插件（注销并关闭 classloader、destroy 工具）。
     *
     * @return true 表示确实卸载了已注册的插件
     */
    private boolean unloadPlugin(String jarName) {
        DynamicToolRegistry.PluginEntry removed = registry.unregister(jarName);
        if (removed != null) {
            logger.info("Unloaded plugin: {} ({} tools released)", jarName, removed.getTools().size());
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