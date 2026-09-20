package com.agent.hopaw.controller;

import com.agent.hopaw.infra.plugin.PluginRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 插件公共调用 API。
 *
 * <p>{@code POST /api/plugins/{pluginId}/invoke} —— 客户端（前端插件 JS、第三方客户端等）
 * 按**插件标识**调用插件的 {@code AgentPlugin.invoke(toolRef, params)} 通用入口，
 * 传入 Map、返回 Map，用于向前端插件发送数据或从插件获取数据。</p>
 *
 * <p>多工具插件由请求体中的 {@code toolRef}（可选，工具集名）做路由，
 * 缺省时由插件自行决定默认行为。</p>
 *
 * <p>返回体即插件 invoke 的返回 Map（插件自行约定内容结构）；
 * 插件不存在返回 404，插件未提供 invoke 能力返回 404，插件内部异常返回 500（body 携带错误信息）。</p>
 */
@RestController
@RequestMapping("/api/plugins")
public class PluginInvokeController {

    private static final Logger log = LoggerFactory.getLogger(PluginInvokeController.class);

    private final PluginRegistry registry;

    public PluginInvokeController(PluginRegistry registry) {
        this.registry = registry;
    }

    @PostMapping("/{pluginId}/invoke")
    public ResponseEntity<Map<String, Object>> invoke(@PathVariable String pluginId,
                                                      @RequestBody(required = false) Map<String, Object> params) {
        PluginRegistry.PluginEntry entry = registry.getPlugin(pluginId);
        if (entry == null) {
            return ResponseEntity.status(404).body(error("plugin not found: " + pluginId));
        }

        Map<String, Object> safeParams = params == null ? Map.of() : params;
        String toolRef = safeParams.get("toolRef") instanceof String ? (String) safeParams.get("toolRef") : null;
        try {
            Map<String, Object> result = entry.getPlugin().invoke(toolRef, safeParams);
            return ResponseEntity.ok(result == null ? Map.of() : result);
        } catch (UnsupportedOperationException e) {
            log.debug("Plugin [{}] does not support invoke", pluginId);
            return ResponseEntity.status(404).body(error("插件 " + pluginId + " 不支持 invoke 调用"));
        } catch (Exception e) {
            log.error("Plugin invoke failed, pluginId={}, toolRef={}, params={}", pluginId, toolRef, safeParams.keySet(), e);
            return ResponseEntity.status(500).body(error(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    private static Map<String, Object> error(String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", message);
        return body;
    }
}
