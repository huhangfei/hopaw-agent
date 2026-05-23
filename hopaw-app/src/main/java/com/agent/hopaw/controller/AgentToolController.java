package com.agent.hopaw.controller;

import com.agent.hopaw.infra.model.dto.PluginInstallResult;
import com.agent.hopaw.infra.model.dto.PluginUpdateInfo;
import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.infra.model.dto.ToolSetInfo;
import com.agent.hopaw.infra.service.ISysConfigService;
import com.agent.hopaw.infra.tool.IAgentToolService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Controller
@RequestMapping("/tools")
public class AgentToolController {

    private static final Logger log = LoggerFactory.getLogger(AgentToolController.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

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
    public SseEmitter installOrUpgrade(@RequestBody PluginUpdateInfo updateInfo) {
        SseEmitter emitter = new SseEmitter(600000L);

        CompletableFuture.runAsync(() -> {
            try {
                PluginInstallResult result = IAgentToolService.installOrUpgradePlugin(updateInfo,
                        stage -> {
                            try {
                                String data = objectMapper.writeValueAsString(Map.of("stage", stage));
                                emitter.send(SseEmitter.event().name("stage").data(data));
                            } catch (Exception e) {
                                log.warn("Failed to send stage event", e);
                            }
                        },
                        percent -> {
                            try {
                                String data = objectMapper.writeValueAsString(Map.of("percent", percent));
                                emitter.send(SseEmitter.event().name("progress").data(data));
                            } catch (Exception e) {
                                log.warn("Failed to send progress event", e);
                            }
                        });

                String resultJson = objectMapper.writeValueAsString(result);
                Thread.sleep(100);
                emitter.send(SseEmitter.event().name("complete").data(resultJson));
                Thread.sleep(100);
                emitter.complete();
            } catch (Exception e) {
                log.error("Install/upgrade failed", e);
                try {
                    String errData = objectMapper.writeValueAsString(Map.of("message", e.getMessage()));
                    emitter.send(SseEmitter.event().name("error").data(errData));
                } catch (Exception ex) {
                    log.warn("Failed to send error event", ex);
                }
                emitter.completeWithError(e);
            }
        });
        return emitter;
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
