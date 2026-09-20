package com.agent.hopaw.controller;

import com.agent.hopaw.infra.model.dto.PluginDescriptor;
import com.agent.hopaw.infra.model.dto.PluginInstallResult;
import com.agent.hopaw.infra.model.dto.PluginUpdateInfo;
import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.infra.model.dto.ToolSetInfo;
import com.agent.hopaw.infra.service.IAgentPluginService;
import com.agent.hopaw.infra.service.IToolSetService;
import com.agent.hopaw.infra.service.PluginConfigService;
import com.agent.hopaw.util.ToolSetViewUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 插件管理端点（一级）：以 pluginId 为标识的列表 / 安装 / 升级 / 卸载 / 导出 / 启停 / 插件级配置。
 *
 * <p>工具集层面的查询见 {@link AgentToolController}（{@code /tools}）。</p>
 */
@Controller
@RequestMapping("/plugins")
public class PluginController {

    private static final Logger log = LoggerFactory.getLogger(PluginController.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final IAgentPluginService pluginService;
    private final PluginConfigService pluginConfigService;
    private final IToolSetService toolSetService;

    public PluginController(IAgentPluginService pluginService, PluginConfigService pluginConfigService,
                            IToolSetService toolSetService) {
        this.pluginService = pluginService;
        this.pluginConfigService = pluginConfigService;
        this.toolSetService = toolSetService;
    }

    // ==================== 页面 ====================

    /** 插件管理页（运维管理一级）：插件列表 + 详情内手风琴展示工具集。 */
    @GetMapping
    public String pluginsPage(Model model) {
        List<PluginDescriptor> plugins = pluginService.getPlugins();
        List<ToolSetInfo> toolSets = toolSetService.getToolSets();
        model.addAttribute("plugins", plugins);
        model.addAttribute("pluginToolSets", ToolSetViewUtil.byPlugin(toolSets, plugins));
        model.addAttribute("activePage", "plugins");
        model.addAttribute("activeTab", "plugins");
        return "plugins";
    }

    // ==================== 查询 ====================

    /** 已安装插件列表（插件管理一级视图）。 */
    @GetMapping("/api/list")
    @ResponseBody
    public ResponseBean list() {
        return ResponseBean.success(pluginService.getPlugins());
    }

    /** 插件配置信息（是否存在插件级/工具级配置项，供卸载确认使用）。 */
    @GetMapping("/api/config-info")
    @ResponseBody
    public ResponseBean configInfo(@RequestParam String pluginId) {
        return ResponseBean.success(pluginService.getPluginConfigInfo(pluginId));
    }

    // ==================== 启停 ====================

    @PostMapping("/api/toggle")
    @ResponseBody
    public ResponseBean toggle(@RequestParam String pluginId, @RequestParam boolean enabled) {
        try {
            pluginService.setEnabled(pluginId, enabled);
            return ResponseBean.success(enabled ? "插件已启用" : "插件已禁用");
        } catch (Exception e) {
            log.warn("切换插件状态失败 pluginId={}, enabled={}", pluginId, enabled, e);
            return ResponseBean.fail(e.getMessage());
        }
    }

    // ==================== 卸载 / 导出 ====================

    @PostMapping("/api/unload")
    @ResponseBody
    public ResponseBean unloadPlugin(@RequestParam String pluginId,
                                     @RequestParam(required = false, defaultValue = "false") boolean cleanConfig) {
        if (pluginService.getPlugin(pluginId) == null) {
            return ResponseBean.fail("插件不存在：" + pluginId);
        }
        boolean result = pluginService.unloadPlugin(pluginId, cleanConfig);
        if (!result) {
            return ResponseBean.fail("插件卸载失败");
        }
        return ResponseBean.success(cleanConfig ? "插件卸载成功，配置已清理" : "插件卸载成功");
    }

    @GetMapping("/api/export/{pluginId}")
    @ResponseBody
    public ResponseEntity<byte[]> exportPlugin(@PathVariable String pluginId) {
        byte[] zipBytes = pluginService.exportPlugin(pluginId);
        if (zipBytes == null) {
            return ResponseEntity.notFound().build();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(pluginId + ".zip").build());
        headers.setContentLength(zipBytes.length);
        return ResponseEntity.ok().headers(headers).body(zipBytes);
    }

    // ==================== 安装 / 升级 ====================

    @PostMapping("/api/install-upgrade")
    public SseEmitter installOrUpgrade(@RequestBody PluginUpdateInfo updateInfo) {
        SseEmitter emitter = new SseEmitter(600000L);

        CompletableFuture.runAsync(() -> {
            try {
                PluginInstallResult result = pluginService.installOrUpgradePlugin(updateInfo,
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

    @PostMapping("/api/local-install")
    @ResponseBody
    public ResponseBean localInstall(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseBean.fail("文件为空");
        }
        try {
            String originalFilename = file.getOriginalFilename();
            PluginInstallResult result;

            // 根据文件扩展名自动判断类型
            if (originalFilename != null && originalFilename.toLowerCase().endsWith(".jar")) {
                // JAR文件：用原始文件名创建临时文件，安装时以原始文件名写入 plugins/ 目录
                String safeName = originalFilename.replaceAll("[\\\\/:*?\"<>|]", "_");
                java.nio.file.Path tempJar = java.nio.file.Files.createTempFile("plugin-install-", "-" + safeName);
                try {
                    file.transferTo(tempJar.toFile());
                    result = pluginService.installPluginFromJarFile(tempJar, safeName);
                } finally {
                    java.nio.file.Files.deleteIfExists(tempJar);
                }
            } else {
                // ZIP文件或其他：使用 ZIP 安装逻辑（插件清单 + JAR）
                result = pluginService.installPluginFromBytes(file.getBytes());
            }

            return ResponseBean.success(result);
        } catch (IllegalArgumentException e) {
            return ResponseBean.fail(e.getMessage());
        } catch (Exception e) {
            log.error("Local install failed", e);
            return ResponseBean.fail("安装失败: " + e.getMessage());
        }
    }

    // ==================== 插件级配置 ====================

    /** 读取插件级配置（plugin.&lt;id&gt;.* 段）。 */
    @GetMapping("/api/config/{pluginId}")
    @ResponseBody
    public ResponseBean getPluginConfig(@PathVariable String pluginId) {
        try {
            return ResponseBean.success(pluginConfigService.getPluginConfig(pluginId));
        } catch (IllegalArgumentException e) {
            return ResponseBean.fail(e.getMessage());
        }
    }

    /** 保存插件级配置。 */
    @PostMapping("/api/config/{pluginId}")
    @ResponseBody
    public ResponseBean savePluginConfig(@PathVariable String pluginId,
                                         @RequestParam Map<String, String> params) {
        try {
            pluginConfigService.savePluginConfig(pluginId, params);
            return ResponseBean.success("配置保存成功");
        } catch (IllegalArgumentException e) {
            return ResponseBean.fail(e.getMessage());
        } catch (Exception e) {
            log.error("保存插件配置失败 pluginId={}", pluginId, e);
            return ResponseBean.fail("保存失败：" + e.getMessage());
        }
    }

    // ==================== 插件级配置页（二级配置页） ====================

    /** 插件级配置页：{@code plugin.<id>.*} 段的可视化编辑（多工具集共享的公共配置）。 */
    @GetMapping("/config/{pluginId}")
    public String pluginConfigPage(@PathVariable String pluginId, Model model) {
        model.addAttribute("config", pluginConfigService.getPluginConfig(pluginId));
        model.addAttribute("pluginId", pluginId);
        model.addAttribute("activePage", "tools");
        model.addAttribute("activeTab", "tools");
        return "plugin-config";
    }

    @PostMapping("/config/{pluginId}")
    public String savePluginConfigPage(@PathVariable String pluginId,
                                       @RequestParam Map<String, String> params,
                                       RedirectAttributes redirectAttributes) {
        try {
            pluginConfigService.savePluginConfig(pluginId, params);
            redirectAttributes.addFlashAttribute("success", "配置保存成功！");
        } catch (Exception e) {
            log.error("保存插件配置失败 pluginId={}", pluginId, e);
            redirectAttributes.addFlashAttribute("error", "保存失败：" + e.getMessage());
        }
        return "redirect:/plugins/config/" + pluginId;
    }
}
