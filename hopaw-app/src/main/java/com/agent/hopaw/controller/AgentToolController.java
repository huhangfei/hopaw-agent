package com.agent.hopaw.controller;

import com.agent.hopaw.infra.model.dto.PluginInstallResult;
import com.agent.hopaw.infra.model.dto.PluginUpdateInfo;
import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.infra.model.dto.ToolSetInfo;
import com.agent.hopaw.infra.service.ISysConfigService;
import com.agent.hopaw.infra.tool.IAgentToolService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/tools")
public class AgentToolController {

    private final IAgentToolService IAgentToolService;
    private final ISysConfigService sysConfigService;

    public AgentToolController(IAgentToolService IAgentToolService, ISysConfigService sysConfigService) {
        this.IAgentToolService = IAgentToolService;
        this.sysConfigService = sysConfigService;
    }

    @GetMapping
    public String toolsPage(Model model) {
        model.addAttribute("toolSets", IAgentToolService.getToolSets());
        return "tools";
    }

    @GetMapping("/api/list")
    @ResponseBody
    public ResponseBean list() {
        return ResponseBean.success(IAgentToolService.getToolSets());
    }

    @PostMapping("/api/unload")
    @ResponseBody
    public ResponseBean unloadPlugin(@RequestParam String jarFileName,
                                     @RequestParam(required = false, defaultValue = "false") boolean cleanConfig) {
        boolean result = IAgentToolService.unloadPlugin(jarFileName);
        if (result) {
            if (cleanConfig) {
                final String toolName = jarFileName;
                final String name = toolName.toLowerCase().endsWith(".jar")
                    ? toolName.substring(0, toolName.length() - 4) : toolName;
                try {
                    var tool = IAgentToolService.getAgentTools().stream()
                            .filter(t -> t.getName().equals(name))
                            .findFirst().orElse(null);
                    if (tool != null && !tool.getConfigItems().isEmpty()) {
                        String prefix = tool.getConfigPrefix();
                        for (var item : tool.getConfigItems()) {
                            String key = prefix + item.getKey();
                            sysConfigService.deleteByKey(key);
                        }
                    }
                } catch (Exception e) {
                    // 配置清理失败不影响卸载结果
                }
            }
            return ResponseBean.success("插件卸载成功");
        }
        return ResponseBean.fail("插件不存在或卸载失败");
    }

    @GetMapping("/api/plugin-config-info")
    @ResponseBody
    public ResponseBean getPluginConfigInfo(@RequestParam String jarFileName) {
        final String toolName = jarFileName.toLowerCase().endsWith(".jar")
            ? jarFileName.substring(0, jarFileName.length() - 4) : jarFileName;
        try {
            var tool = IAgentToolService.getAgentTools().stream()
                    .filter(t -> t.getName().equals(toolName))
                    .findFirst().orElse(null);
            if (tool != null && !tool.getConfigItems().isEmpty()) {
                return ResponseBean.success(java.util.Map.of(
                        "hasConfig", true,
                        "configKeys", tool.getConfigItems().stream()
                                .map(item -> tool.getConfigPrefix() + item.getKey())
                                .toList()
                ));
            }
        } catch (Exception e) {
            // ignore
        }
        return ResponseBean.success(java.util.Map.of("hasConfig", false));
    }

    @PostMapping("/api/install-upgrade")
    @ResponseBody
    public ResponseBean installOrUpgrade(@RequestBody PluginUpdateInfo updateInfo) {
        PluginInstallResult result = IAgentToolService.installOrUpgradePlugin(updateInfo);
        if (result.isSuccess()) {
            return ResponseBean.success(result);
        }
        return ResponseBean.fail(result.getMessage());
    }

    @GetMapping("/api/export/{jarFileName}")
    @ResponseBody
    public ResponseEntity<byte[]> exportPlugin(@PathVariable String jarFileName) {
        byte[] zipBytes = IAgentToolService.exportPlugin(jarFileName);
        if (zipBytes == null) {
            return ResponseEntity.notFound().build();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        String exportFileName = jarFileName;
        if (exportFileName.toLowerCase().endsWith(".jar")) {
            exportFileName = exportFileName.substring(0, exportFileName.length() - 4);
        }
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(exportFileName + ".zip").build());
        headers.setContentLength(zipBytes.length);
        return ResponseEntity.ok().headers(headers).body(zipBytes);
    }
}
