package com.agent.hopaw.controller;

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
    public ResponseBean unloadPlugin(@RequestParam String jarFileName) {
        boolean result = IAgentToolService.unloadPlugin(jarFileName);
        if (result) {
            return ResponseBean.success("插件卸载成功");
        }
        return ResponseBean.fail("插件不存在或卸载失败");
    }

    @GetMapping("/upgrade")
    public String upgradePage(@RequestParam String toolName,
                               @RequestParam(required = false) String currentVersion,
                               @RequestParam(required = false) String jarFileName,
                               @RequestParam(required = false) String updateUrl,
                               Model model) {
        model.addAttribute("toolName", toolName);
        model.addAttribute("currentVersion", currentVersion != null ? currentVersion : "");
        model.addAttribute("jarFileName", jarFileName != null ? jarFileName : "");
        model.addAttribute("defaultUpdateUrl", updateUrl != null ? updateUrl : "");
        return "tool-upgrade";
    }

    @PostMapping("/api/check-update")
    @ResponseBody
    public ResponseBean checkUpdate(@RequestParam String checkUrl,
                                    @RequestParam String toolName,
                                    @RequestParam(required = false) String currentVersion,
                                    @RequestParam(required = false) String jarFileName) {
        PluginUpdateInfo updateInfo = IAgentToolService.checkPluginUpdate(checkUrl, toolName, currentVersion, jarFileName);
        return ResponseBean.success(updateInfo);
    }

    @PostMapping("/api/install-upgrade")
    @ResponseBody
    public ResponseBean installOrUpgrade(@RequestBody PluginUpdateInfo updateInfo) {
        boolean result = IAgentToolService.installOrUpgradePlugin(updateInfo);
        if (result) {
            return ResponseBean.success("安装/升级成功");
        }
        return ResponseBean.fail("安装/升级失败");
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
