package com.agent.hopaw.controller;

import com.agent.hopaw.infra.plugin.DynamicToolRegistry;
import com.agent.hopaw.infra.tool.AgentTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 插件公共调用 API。
 *
 * <p>{@code POST /api/plugins/{toolName}/invoke} —— 客户端（前端插件 JS、第三方客户端等）
 * 按工具名直接调用插件的 {@link AgentTool#invoke(Map)} 通用入口，传入 Map、返回 Map，
 * 用于向前端插件发送数据或从插件获取数据。</p>
 *
 * <p>查找范围：jar 动态插件优先，其次内置工具 Spring Bean。
 * 返回体即插件 invoke 的返回 Map（插件自行约定内容结构）；
 * 工具不存在返回 404，插件内部异常返回 500（body 携带错误信息）。</p>
 */
@RestController
@RequestMapping("/api/plugins")
public class PluginInvokeController {

    private static final Logger log = LoggerFactory.getLogger(PluginInvokeController.class);

    private final DynamicToolRegistry registry;

    /** 所有内置工具 Bean（hopaw-biz 等模块中实现 AgentTool 的 @Component）。 */
    private final ObjectProvider<AgentTool> builtinTools;

    public PluginInvokeController(DynamicToolRegistry registry, ObjectProvider<AgentTool> builtinTools) {
        this.registry = registry;
        this.builtinTools = builtinTools;
    }

    @PostMapping("/{toolName}/invoke")
    public ResponseEntity<Map<String, Object>> invoke(@PathVariable String toolName,
                                                      @RequestBody(required = false) Map<String, Object> params) {
        AgentTool tool = findTool(toolName);
        if (tool == null) {
            Map<String, Object> body = new HashMap<>();
            body.put("success", false);
            body.put("message", "tool not found: " + toolName);
            return ResponseEntity.status(404).body(body);
        }

        Map<String, Object> safeParams = params == null ? Map.of() : params;
        try {
            Map<String, Object> result = tool.invoke(safeParams);
            return ResponseEntity.ok(result == null ? Map.of() : result);
        } catch (Exception e) {
            log.error("Plugin invoke failed, tool={}, params={}", toolName, safeParams.keySet(), e);
            Map<String, Object> body = new HashMap<>();
            body.put("success", false);
            body.put("message", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            return ResponseEntity.status(500).body(body);
        }
    }

    /** 按工具名查找：jar 动态插件优先（可热更覆盖），其次内置工具 Bean。 */
    private AgentTool findTool(String toolName) {
        for (AgentTool tool : registry.getAllDynamicTools()) {
            if (toolName.equals(tool.getName())) {
                return tool;
            }
        }
        for (AgentTool tool : builtinTools.stream().collect(Collectors.toList())) {
            if (toolName.equals(tool.getName())) {
                return tool;
            }
        }
        return null;
    }
}
