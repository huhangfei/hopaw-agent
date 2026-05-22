package com.agent.hopaw.infra.plugin;

import com.agent.hopaw.infra.model.dto.ToolSetInfo;
import com.agent.hopaw.infra.tool.AgentTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Component
public class DynamicToolRegistry {

    private static final Logger logger = LoggerFactory.getLogger(DynamicToolRegistry.class);

    private final ConcurrentMap<String, PluginEntry> plugins = new ConcurrentHashMap<>();

    public void register(String jarFileName, PluginClassLoader classLoader, List<AgentTool> tools) {
        PluginEntry entry = new PluginEntry(jarFileName, classLoader, tools);
        plugins.put(jarFileName, entry);
        logger.info("Registered plugin [{}] with {} tools", jarFileName, tools.size());
    }

    public PluginEntry unregister(String jarFileName) {
        PluginEntry entry = plugins.remove(jarFileName);
        if (entry != null) {
            // 调用所有工具的 destroy 方法（如果存在）
            for (AgentTool tool : entry.tools) {
                try {
                    tool.destroy();
                    logger.debug("Called destroy() on tool: {}", tool.getName());
                 } catch (Exception e) {
                    logger.error("Error calling destroy() on tool: {}", tool.getClass().getSimpleName(), e);
                }
            }
            logger.info("Unregistered plugin [{}]", jarFileName);
        }
        return entry;
    }

    public List<AgentTool> getAllDynamicTools() {
        List<AgentTool> result = new ArrayList<>();
        for (PluginEntry entry : plugins.values()) {
            result.addAll(entry.tools);
        }
        return Collections.unmodifiableList(result);
    }
    public List<PluginEntry> getAllPluginEntries() {
        return Collections.unmodifiableList(plugins.values().stream().collect(Collectors.toList()));
    }


    public boolean hasPlugin(String jarFileName) {
        return plugins.containsKey(jarFileName);
    }

    public List<String> getPluginNames() {
        return new ArrayList<>(plugins.keySet());
    }

    public Map<String, PluginEntry> getPlugins() {
        return Collections.unmodifiableMap(plugins);
    }

    public boolean isEmpty() {
        return plugins.isEmpty();
    }

    public static class PluginEntry {
        public final String jarFileName;
        public final PluginClassLoader classLoader;
        public final List<AgentTool> tools;
        // 缓存该插件的所有资源文件内容 (资源路径 -> 内容)
        private final Map<String, String> resourceCache = new ConcurrentHashMap<>();

        public PluginEntry(String jarFileName, PluginClassLoader classLoader, List<AgentTool> tools) {
            this.jarFileName = jarFileName;
            this.classLoader = classLoader;
            this.tools = Collections.unmodifiableList(tools);
        }
        
        /**
         * 获取缓存的资源内容,如果不存在则从 JAR 中加载
         */
        public String getCachedResource(String resourcePath) {
            return resourceCache.computeIfAbsent(resourcePath, this::loadResourceFromJar);
        }
        
        /**
         * 从 JAR 文件中加载资源内容
         */
        private String loadResourceFromJar(String resourcePath) {
            try (InputStream is = classLoader.getResourceAsStream(resourcePath)) {
                if (is != null) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                        String content = reader.lines().collect(Collectors.joining("\n"));
                        logger.debug("Loaded resource from classloader cache: {} ({} bytes)", resourcePath, content.length());
                        return content;
                    }
                }
            } catch (IOException e) {
                logger.error("Failed to load resource: {}", resourcePath, e);
            }
            logger.warn("Resource not found: {}", resourcePath);
            return "";
        }
    }
}