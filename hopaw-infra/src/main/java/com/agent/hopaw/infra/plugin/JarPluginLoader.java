package com.agent.hopaw.infra.plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

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
    private final Thread watcherThread;
    private volatile boolean running = true;

    private final ConcurrentMap<String, Long> recentEvents = new ConcurrentHashMap<>();

    public JarPluginLoader(DynamicToolRegistry registry,
                           AutowireCapableBeanFactory beanFactory,
                           @Value("${agent.plugin.dir:plugins}") String pluginDirPath) {
        this.registry = registry;
        this.beanFactory = beanFactory;
        this.pluginDir = new File(pluginDirPath).toPath().toAbsolutePath().normalize();
        this.watcherThread = new Thread(this::watchLoop, "plugin-watcher");
        this.watcherThread.setDaemon(true);
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

            watcherThread.start();
            logger.info("Plugin watcher started, monitoring: {}", pluginDir);
        } catch (Throwable ex) {
            // 捕获所有异常和错误（包括 NoClassDefFoundError、NoSuchMethodError 等）
            // 确保插件加载失败不会影响应用启动
            logger.error("Error during plugin initialization (non-fatal, application will continue)", ex);
        }
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        watcherThread.interrupt();
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

    @SuppressWarnings("unchecked")
    private void watchLoop() {
        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            pluginDir.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);

            while (running) {
                WatchKey key;
                try {
                    key = watchService.poll(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                if (key == null) {
                    continue;
                }

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }

                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path fileName = ev.context();
                    String name = fileName.toString();
                    if (!name.endsWith(".jar")) {
                        continue;
                    }

                    long now = System.currentTimeMillis();
                    String eventKey = name + ":" + kind.name();
                    Long lastEvent = recentEvents.get(eventKey);
                    if (lastEvent != null && now - lastEvent < 500) {
                        continue;
                    }
                    recentEvents.put(eventKey, now);

                    File jarFile = pluginDir.resolve(fileName).toFile();

                    if (kind == StandardWatchEventKinds.ENTRY_CREATE
                            || kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                        if (jarFile.exists() && jarFile.length() > 0) {
                            loadPlugin(jarFile);
                        }
                    } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                        if (!jarFile.exists()) {
                            unloadPlugin(name);
                        }
                    }
                }

                boolean valid = key.reset();
                if (!valid) {
                    logger.warn("Watch key invalid, plugin watching may stop");
                    break;
                }
            }
        } catch (IOException e) {
            logger.error("Plugin watcher error", e);
        }
    }

    private void loadPlugin(File jarFile) {
        String jarName = jarFile.getName();
        if (registry.hasPlugin(jarName)) {
            logger.debug("Plugin already loaded: {}", jarName);
            return;
        }

        try {
            PluginClassLoader classLoader = new PluginClassLoader(jarFile);
            List<String> classNames = classLoader.scanAgentToolClasses();

            if (classNames.isEmpty()) {
                logger.warn("No AgentTool implementation found in: {}", jarName);
                return;
            }

            List<AgentTool> tools = new ArrayList<>();
            for (String className : classNames) {
                try {
                    Class<?> clazz = classLoader.loadClass(className);
                    AgentTool tool = (AgentTool) clazz.getDeclaredConstructor().newInstance();
                    // 使用 Spring 的依赖注入功能为插件工具注入依赖
                    beanFactory.autowireBean(tool);
                    tool.asyncInit();
                    tools.add(tool);
                } catch (Exception e) {
                    logger.error("Failed to load tool class: {}", className, e);
                }
            }

            registry.register(jarName, classLoader, tools);
            logger.info("Loaded plugin: {} with {} tools", jarName, tools.size());
        } catch (Throwable e) {
            // 捕获所有异常和错误，确保单个插件加载失败不影响其他插件和应用启动
            logger.error("Failed to load plugin: {}", jarName, e);
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