package com.agent.hopaw.controller;

import com.agent.hopaw.infra.plugin.PluginRegistry;
import com.agent.hopaw.infra.plugin.PluginAsset;
import com.agent.hopaw.infra.service.IAgentPluginService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 插件前端资源暴露端点。
 *
 * <p>两个能力：</p>
 * <ol>
 *   <li>{@code GET /api/plugins/assets?page=} —— 前端 loader 拉取当前页面需要的资源清单（已禁用插件不返回）；</li>
 *   <li>{@code GET /api/plugins/{pluginId}/assets/**} —— 流式返回 JAR 内 static/ 资源（路径穿越防护 + immutable 缓存）。</li>
 * </ol>
 *
 * <p>路径变量 {@code pluginId} 为插件标识（不再是 JAR 文件名）。</p>
 */
@RestController
@RequestMapping("/api/plugins")
public class PluginAssetController {

    private static final Logger log = LoggerFactory.getLogger(PluginAssetController.class);

    private final PluginRegistry registry;
    private final IAgentPluginService pluginService;

    public PluginAssetController(PluginRegistry registry, IAgentPluginService pluginService) {
        this.registry = registry;
        this.pluginService = pluginService;
    }

    /**
     * 前端页面调用：拉取给定页面标识需要的资源清单（已按 priority 升序，已禁用插件不注入）。
     */
    @GetMapping("/assets")
    public List<Map<String, Object>> list(@RequestParam(required = false) String page) {
        List<PluginAsset> assets = registry.assetsForPage(page == null ? "" : page);
        List<Map<String, Object>> result = new ArrayList<>(assets.size());
        for (PluginAsset a : assets) {
            if (!pluginService.isEnabled(a.getPlugin())) {
                log.debug("Skip assets of disabled plugin [{}]", a.getPlugin());
                continue;
            }
            Map<String, Object> item = new HashMap<>();
            item.put("id", a.getId());
            item.put("plugin", a.getPlugin());
            item.put("type", a.getType());
            item.put("url", "/api/plugins/" + a.getPlugin() + "/assets/" + a.getPath() + "?v=" + a.getVersion());
            item.put("position", a.getPosition());
            item.put("defer", a.isDefer());
            item.put("priority", a.getPriority());
            item.put("mount", a.getMount() == null ? "" : a.getMount());
            item.put("mode", a.getMode());
            result.add(item);
        }
        return result;
    }

    /**
     * 实际资源下载。路径来自 URL，需严格防护路径穿越，仅允许 static/ 前缀。
     */
    @GetMapping("/{pluginId}/assets/**")
    public ResponseEntity<byte[]> serve(@PathVariable String pluginId, HttpServletRequest request) throws IOException {
        String fullPath = (String) request.getAttribute(
                org.springframework.web.servlet.HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        if (fullPath == null) {
            fullPath = request.getRequestURI();
        }
        String prefix = "/api/plugins/" + pluginId + "/assets/";
        int idx = fullPath.indexOf(prefix);
        if (idx < 0) {
            return ResponseEntity.notFound().build();
        }
        String path = fullPath.substring(idx + prefix.length());

        // 三重路径穿越防护
        if (path == null || path.isEmpty() || !path.startsWith("static/") || path.contains("..")) {
            log.warn("Rejected invalid asset path: {}", path);
            return ResponseEntity.status(404).build();
        }
        Path normalized = Paths.get(path).normalize();
        if (!normalized.startsWith("static")) {
            log.warn("Rejected path traversal asset: {}", path);
            return ResponseEntity.status(404).build();
        }

        PluginRegistry.PluginEntry entry = registry.getPlugin(pluginId);
        if (entry == null) {
            return ResponseEntity.notFound().build();
        }
        if (!pluginService.isEnabled(pluginId)) {
            log.debug("Rejected asset request for disabled plugin [{}]", pluginId);
            return ResponseEntity.status(404).build();
        }

        try (InputStream is = entry.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                return ResponseEntity.notFound().build();
            }
            byte[] body = is.readAllBytes();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, guessContentType(path))
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
                    .header(HttpHeaders.ETAG, "\"" + entry.getClassLoader().getJarFile().lastModified() + "\"")
                    .body(body);
        }
    }

    private String guessContentType(String path) {
        if (path.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        if (path.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (path.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (path.endsWith(".json")) {
            return "application/json; charset=utf-8";
        }
        if (path.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (path.endsWith(".png")) {
            return "image/png";
        }
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (path.endsWith(".gif")) {
            return "image/gif";
        }
        return "application/octet-stream";
    }
}
