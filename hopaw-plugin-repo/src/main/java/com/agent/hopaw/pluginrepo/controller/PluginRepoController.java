package com.agent.hopaw.pluginrepo.controller;

import com.agent.hopaw.pluginrepo.service.AuthService;
import com.agent.hopaw.infra.model.dto.PluginRepoResult;
import com.agent.hopaw.pluginrepo.service.PluginRepoService;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/plugin-repo")
public class PluginRepoController {

    private final PluginRepoService pluginRepoService;
    private final AuthService authService;

    public PluginRepoController(PluginRepoService pluginRepoService, AuthService authService) {
        this.pluginRepoService = pluginRepoService;
        this.authService = authService;
    }

    @GetMapping({"", "/"})
    public String index(Model model) throws IOException {
        List<PluginRepoResult> plugins = pluginRepoService.scanPlugins();
        model.addAttribute("plugins", plugins);
        model.addAttribute("currentUser", authService.getCurrentUser());
        return "plugin-repo";
    }

    @GetMapping("/docs")
    public String docs(Model model) {
        model.addAttribute("currentUser", authService.getCurrentUser());
        return "api-docs";
    }

    @GetMapping("/api/plugins")
    @ResponseBody
    public List<PluginRepoResult> apiListPlugins() throws IOException {
        return pluginRepoService.scanPlugins();
    }

    @PostMapping("/web/import")
    @ResponseBody
    public ResponseEntity<?> webImport(@RequestParam("file") MultipartFile file) {
        if (!authService.isAdmin()) {
            return ResponseEntity.status(403).body(Map.of("type", "error", "message", "无权限操作"));
        }

        try {
            PluginRepoResult result = pluginRepoService.importPlugin(file);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("导入失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/web/plugin/{pluginName}/{version}")
    @ResponseBody
    public ResponseEntity<?> webDelete(@PathVariable String pluginName,
                                       @PathVariable String version) {
        if (!authService.isAdmin()) {
            return ResponseEntity.status(403).body(Map.of("type", "error", "message", "无权限操作"));
        }

        try {
            pluginRepoService.deletePlugin(pluginName, version);
            return ResponseEntity.ok(Map.of("type", "success", "message", "删除成功"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("type", "error", "message", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("type", "error", "message", "删除失败: " + e.getMessage()));
        }
    }

    @GetMapping("/api/download/{pluginName}/{version}")
    @ResponseBody
    public ResponseEntity<byte[]> apiDownload(@PathVariable String pluginName,
                                               @PathVariable String version) throws IOException {
        byte[] zipBytes = pluginRepoService.getPluginDownload(pluginName, version);
        if (zipBytes == null) {
            return ResponseEntity.notFound().build();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        String fileName = pluginName + "-" + version + ".zip";
        headers.setContentDisposition(ContentDisposition.attachment().filename(fileName).build());
        headers.setContentLength(zipBytes.length);
        return ResponseEntity.ok().headers(headers).body(zipBytes);
    }
}