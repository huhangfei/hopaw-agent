package com.agent.hopaw.controller;

import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.infra.service.ToolStateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 工具运行态开关端点：工具集级 / 工具方法级 禁用启用。
 *
 * <p>插件级开关在 {@link PluginController}（{@code /plugins/api/toggle}）；
 * 本控制器只负责工具集级与工具方法级（{@code tool_state} 表）。</p>
 */
@Controller
@RequestMapping("/api/tool-state")
public class ToolStateController {

    private static final Logger log = LoggerFactory.getLogger(ToolStateController.class);

    private final ToolStateService toolStateService;

    public ToolStateController(ToolStateService toolStateService) {
        this.toolStateService = toolStateService;
    }

    /** 切换工具集启用状态。 */
    @PostMapping("/toolset")
    @ResponseBody
    public ResponseBean toggleToolSet(@RequestParam String toolSetName, @RequestParam boolean enabled) {
        try {
            toolStateService.setToolSetEnabled(toolSetName, enabled);
            return ResponseBean.successMsg(enabled ? "工具集已启用" : "工具集已禁用");
        } catch (Exception e) {
            log.warn("切换工具集状态失败 toolSetName={}, enabled={}", toolSetName, enabled, e);
            return ResponseBean.fail(e.getMessage());
        }
    }

    /** 切换工具方法启用状态。 */
    @PostMapping("/tool")
    @ResponseBody
    public ResponseBean toggleTool(@RequestParam String toolSetName,
                                   @RequestParam String toolName,
                                   @RequestParam boolean enabled) {
        try {
            toolStateService.setToolEnabled(toolSetName, toolName, enabled);
            return ResponseBean.successMsg(enabled ? "工具方法已启用" : "工具方法已禁用");
        } catch (Exception e) {
            log.warn("切换工具方法状态失败 toolSetName={}, toolName={}, enabled={}", toolSetName, toolName, enabled, e);
            return ResponseBean.fail(e.getMessage());
        }
    }
}
