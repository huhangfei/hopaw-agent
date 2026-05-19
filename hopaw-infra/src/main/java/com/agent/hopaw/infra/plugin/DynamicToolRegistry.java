package com.agent.hopaw.infra.plugin;

import com.agent.hopaw.infra.tool.AgentTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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

    public boolean hasPlugin(String jarFileName) {
        return plugins.containsKey(jarFileName);
    }

    public List<String> getPluginNames() {
        return new ArrayList<>(plugins.keySet());
    }

    public boolean isEmpty() {
        return plugins.isEmpty();
    }

    public static class PluginEntry {
        public final String jarFileName;
        public final PluginClassLoader classLoader;
        public final List<AgentTool> tools;

        public PluginEntry(String jarFileName, PluginClassLoader classLoader, List<AgentTool> tools) {
            this.jarFileName = jarFileName;
            this.classLoader = classLoader;
            this.tools = Collections.unmodifiableList(tools);
        }
    }
}