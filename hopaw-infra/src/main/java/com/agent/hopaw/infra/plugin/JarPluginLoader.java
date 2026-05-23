package com.agent.hopaw.infra.plugin;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
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
        } catch (Throwable ex) {
            // 捕获所有异常和错误（包括 NoClassDefFoundError、NoSuchMethodError 等）
            // 确保插件加载失败不会影响应用启动
            logger.error("Error during plugin initialization (non-fatal, application will continue)", ex);
        }
    }

    @PreDestroy
    public void shutdown() {
        for (String jarName : registry.getPluginNames()) {
            registry.unregister(jarName);
        }
        logger.info("Plugin loader shutdown, all plugins unloaded");
    }

    private void initialScan(File dir) {
        File[] jars = dir.listFiles((f, name) -> name.endsWith(".jar"));
        if (jars != null) {
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

        try {
            PluginClassLoader classLoader = new PluginClassLoader(jarFile);
            List<String> classNames = classLoader.scanAgentToolClasses();

            if (classNames.isEmpty()) {
                logger.warn("No AgentTool implementation found in: {}", jarName);
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

            registry.register(jarName, classLoader, tools);
            logger.info("Loaded plugin: {} with {} tools", jarName, tools.size());
            return tools.size();
        } catch (Throwable e) {
            logger.error("Failed to load plugin: {}", jarName, e);
            return 0;
        }
    }

    public PluginScanResult scanPluginInfo(File jarFile) {
        String jarName = jarFile.getName();
        PluginScanResult result = new PluginScanResult();
        result.jarFileName = jarName;

        try {
            PluginClassLoader classLoader = new PluginClassLoader(jarFile);
            List<String> classNames = classLoader.scanAgentToolClasses();

            if (classNames.isEmpty()) {
                result.errorMessage = "No AgentTool implementation found";
                return result;
            }

            for (String className : classNames) {
                try {
                    Class<?> clazz = classLoader.loadClass(className);
                    AgentTool tool = (AgentTool) clazz.getDeclaredConstructor().newInstance();
                    result.pluginName = tool.getName();
                    result.toolNames = new ArrayList<>();
                    for (java.lang.reflect.Method method : clazz.getMethods()) {
                        dev.langchain4j.agent.tool.Tool toolAnn = method.getAnnotation(dev.langchain4j.agent.tool.Tool.class);
                        if (toolAnn != null) {
                            String toolName = toolAnn.name();
                            if (toolName.isEmpty()) {
                                toolName = method.getName();
                            }
                            result.toolNames.add(toolName);
                        }
                    }
                    break;
                } catch (Exception e) {
                    logger.debug("Failed to scan tool class: {}", className, e);
                }
            }
        } catch (Throwable e) {
            result.errorMessage = e.getMessage();
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

    private void unloadPlugin(String jarName) {
        DynamicToolRegistry.PluginEntry removed = registry.unregister(jarName);
        if (removed != null) {
            logger.info("Unloaded plugin: {} ({} tools released)", jarName, removed.tools.size());
        }
    }

    public boolean unloadAndDeletePlugin(String jarName) {
        DynamicToolRegistry.PluginEntry removed = registry.unregister(jarName);
        if (removed == null) {
            logger.warn("Plugin not found for unload: {}", jarName);
            return false;
        }
        logger.info("Unloaded plugin: {} ({} tools released)", jarName, removed.tools.size());

        File jarFile = pluginDir.resolve(jarName).toFile();
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
}
