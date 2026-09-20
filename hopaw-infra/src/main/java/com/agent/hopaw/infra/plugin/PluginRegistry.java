package com.agent.hopaw.infra.plugin;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * 插件注册表：以 {@link AgentPlugin} 为主体，主键为 pluginId。
 *
 * <p>与旧 DynamicToolRegistry 的区别：注册单位从「JAR 文件名」提升为「插件标识」，
 * 一个条目持有一个 AgentPlugin 实例 + 其分发的 0..N 个 AgentTool + 前端资产。
 * JAR 文件名降级为条目的物理属性，仅用于文件卸载等物理操作。</p>
 */
@Component
public class PluginRegistry {

    private static final Logger logger = LoggerFactory.getLogger(PluginRegistry.class);

    /** pluginId -> PluginEntry */
    private final ConcurrentMap<String, PluginEntry> plugins = new ConcurrentHashMap<>();

    /**
     * 注册插件。并发安全：使用 putIfAbsent 原子化，拒绝重复注册同一 pluginId。
     *
     * @return true 表示本次成功注册；false 表示同名插件已存在（本次被拒绝），
     *         调用方需自行关闭传入的 classLoader。
     */
    public boolean register(AgentPlugin plugin, String jarFileName, PluginClassLoader classLoader, List<AgentTool> tools) {
        PluginEntry entry = new PluginEntry(plugin, jarFileName, classLoader, tools);
        boolean added = plugins.putIfAbsent(plugin.getId(), entry) == null;
        if (added) {
            logger.info("Registered plugin [{}] (jar={}) with {} tools", plugin.getId(), jarFileName, tools.size());
        } else {
            logger.warn("Plugin [{}] already registered, ignore duplicate registration", plugin.getId());
        }
        return added;
    }

    /**
     * 按插件标识卸载：先销毁各工具，再销毁插件，最后关闭 classloader。
     */
    public PluginEntry unregister(String pluginId) {
        PluginEntry entry = plugins.remove(pluginId);
        if (entry != null) {
            destroyEntry(entry);
        }
        return entry;
    }

    /**
     * 按 JAR 文件名卸载（物理文件操作路径使用）。
     */
    public PluginEntry unregisterByJarFileName(String jarFileName) {
        PluginEntry entry = findByJarFileName(jarFileName);
        if (entry == null) {
            return null;
        }
        return unregister(entry.getPluginId());
    }

    private PluginEntry findByJarFileName(String jarFileName) {
        for (PluginEntry entry : plugins.values()) {
            if (jarFileName.equals(entry.getJarFileName())) {
                return entry;
            }
        }
        return null;
    }

    /**
     * 销毁顺序契约：逐工具 destroy → 插件 destroy → 关闭 classloader。
     * 任何一步异常都不影响后续清理。
     */
    private void destroyEntry(PluginEntry entry) {
        for (AgentTool tool : entry.getTools()) {
            try {
                tool.destroy();
                logger.debug("Called destroy() on tool: {}", tool.getName());
            } catch (Exception e) {
                logger.error("Error calling destroy() on tool: {}", tool.getClass().getSimpleName(), e);
            }
        }
        try {
            entry.getPlugin().destroy();
        } catch (Exception e) {
            logger.error("Error calling destroy() on plugin: {}", entry.getPluginId(), e);
        }
        // 关闭 classloader，释放 JAR 文件句柄与临时文件，保证 Windows 上可删除 JAR
        try {
            entry.getClassLoader().close();
        } catch (Exception e) {
            logger.error("Error closing plugin classloader [{}]", entry.getPluginId(), e);
        }
        logger.info("Unregistered plugin [{}]", entry.getPluginId());
    }

    public List<AgentTool> getAllPluginTools() {
        List<AgentTool> result = new ArrayList<>();
        for (PluginEntry entry : plugins.values()) {
            result.addAll(entry.getTools());
        }
        return Collections.unmodifiableList(result);
    }

    public List<PluginEntry> getAllPluginEntries() {
        return List.copyOf(plugins.values());
    }

    public boolean hasPlugin(String pluginId) {
        return plugins.containsKey(pluginId);
    }

    public boolean hasPluginJar(String jarFileName) {
        return findByJarFileName(jarFileName) != null;
    }

    /**
     * 按插件标识获取插件条目。
     */
    public PluginEntry getPlugin(String pluginId) {
        return plugins.get(pluginId);
    }

    /**
     * 按 JAR 文件名获取插件条目。
     */
    public PluginEntry getPluginByJarFileName(String jarFileName) {
        return findByJarFileName(jarFileName);
    }

    /**
     * 聚合所有插件中匹配给定页面标识的前端资源，按 priority 升序返回。
     *
     * @param page 页面标识（复用 activePage，如 index / tools）
     */
    public List<PluginAsset> assetsForPage(String page) {
        List<PluginAsset> all = new ArrayList<>();
        for (PluginEntry entry : plugins.values()) {
            all.addAll(entry.getAssets());
        }
        return all.stream()
                .filter(a -> a.matches(page))
                .sorted(java.util.Comparator.comparingInt(PluginAsset::getPriority))
                .collect(Collectors.toList());
    }

    /**
     * 已注册插件标识列表（按标识排序，保证展示顺序稳定）。
     */
    public List<String> getPluginIds() {
        List<String> ids = new ArrayList<>(plugins.keySet());
        Collections.sort(ids);
        return ids;
    }

    public Map<String, PluginEntry> getPlugins() {
        return Collections.unmodifiableMap(plugins);
    }

    public boolean isEmpty() {
        return plugins.isEmpty();
    }

    /**
     * 插件条目：一个 AgentPlugin 实例 + 其工具与前端资产。
     */
    public static class PluginEntry {
        private final AgentPlugin plugin;
        private final String jarFileName;
        private final PluginClassLoader classLoader;
        private final List<AgentTool> tools;
        // 缓存该插件的所有资源文件内容 (资源路径 -> 内容)
        private final Map<String, String> resourceCache = new ConcurrentHashMap<>();
        // 前端资源清单（plugin-assets.json 解析结果，构造时解析一次，仅缓存清单本身）
        private volatile List<PluginAsset> assets;

        public PluginEntry(AgentPlugin plugin, String jarFileName, PluginClassLoader classLoader, List<AgentTool> tools) {
            this.plugin = plugin;
            this.jarFileName = jarFileName;
            this.classLoader = classLoader;
            this.tools = Collections.unmodifiableList(tools);
            this.assets = loadAssets();
        }

        public AgentPlugin getPlugin() {
            return plugin;
        }

        public String getPluginId() {
            return plugin.getId();
        }

        public String getPluginName() {
            return plugin.getName();
        }

        public String getJarFileName() {
            return jarFileName;
        }

        public PluginClassLoader getClassLoader() {
            return classLoader;
        }

        public List<AgentTool> getTools() {
            return tools;
        }

        public List<PluginAsset> getAssets() {
            return assets;
        }

        /**
         * 解析 JAR 根目录的 plugin-assets.json，返回前端资源清单。
         * 仅在构造时调用一次；清单本身小，直接缓存；资源本体不在此处读取（避免堆缓存 OOM）。
         */
        private List<PluginAsset> loadAssets() {
            try (InputStream is = classLoader.getResourceAsStream("plugin-assets.json")) {
                if (is == null) {
                    return List.of();
                }
                String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                com.alibaba.fastjson2.JSONObject root = com.alibaba.fastjson2.JSON.parseObject(json);
                if (root == null || root.getJSONArray("assets") == null) {
                    return List.of();
                }
                String version = Long.toHexString(classLoader.getJarFile().lastModified());
                List<PluginAsset> result = new ArrayList<>();
                for (Object item : root.getJSONArray("assets")) {
                    com.alibaba.fastjson2.JSONObject node = (com.alibaba.fastjson2.JSONObject) item;
                    String type = node.getString("type");
                    String path = node.getString("path");
                    // 只允许暴露 static/ 目录，避免 class 或 FAT JAR 内部路径被读出
                    if (path == null || !path.startsWith("static/") || path.contains("..")) {
                        logger.warn("Skip invalid plugin asset path in {}: {}", jarFileName, path);
                        continue;
                    }
                    List<String> pages = new ArrayList<>();
                    if (node.getJSONArray("pages") != null) {
                        for (Object p : node.getJSONArray("pages")) {
                            pages.add(String.valueOf(p));
                        }
                    }
                    result.add(new PluginAsset(
                            node.getString("id"),
                            plugin.getId(),
                            type,
                            path,
                            pages,
                            node.getString("position") != null ? node.getString("position") : "body-end",
                            node.getBooleanValue("defer"),
                            node.getIntValue("priority", 1000),
                            node.getString("mount"),
                            node.getString("mode") != null ? node.getString("mode") : "append",
                            version
                    ));
                }
                if (!result.isEmpty()) {
                    logger.info("Loaded {} frontend assets from {} (version {})", result.size(), jarFileName, version);
                }
                return result;
            } catch (Exception e) {
                logger.error("Failed to load plugin-assets.json from {}", jarFileName, e);
                return List.of();
            }
        }

        /**
         * 获取缓存的资源内容，如果不存在则从 JAR 中加载；加载失败或资源为空时不缓存。
         */
        public String getCachedResource(String resourcePath) {
            String cached = resourceCache.get(resourcePath);
            if (cached != null) {
                return cached;
            }
            String content = loadResourceFromJar(resourcePath);
            if (content != null && !content.isEmpty()) {
                resourceCache.putIfAbsent(resourcePath, content);
                return content;
            }
            return "";
        }

        /**
         * 从 JAR 文件中加载资源内容；失败返回 null（由调用方决定是否缓存）。
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
            return null;
        }
    }
}
