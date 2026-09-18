package com.agent.hopaw.controller;

import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.infra.model.entity.McpServerConfig;
import com.agent.hopaw.infra.service.IMcpServerConfigService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mcp")
public class McpServerController {

    private final IMcpServerConfigService mcpService;

    public McpServerController(IMcpServerConfigService mcpService) {
        this.mcpService = mcpService;
    }

    @GetMapping
    public ResponseBean list() {
        List<McpServerConfig> list = mcpService.findAll();
        return ResponseBean.success(list);
    }

    @GetMapping("/{id}")
    public ResponseBean get(@PathVariable Long id) {
        McpServerConfig config = mcpService.findById(id);
        if (config == null) {
            return ResponseBean.fail("MCP 服务器不存在");
        }
        return ResponseBean.success(config);
    }

    @PostMapping
    public ResponseBean create(@RequestBody McpServerConfig config) {
        mcpService.insert(config);
        return ResponseBean.success(config);
    }

    @PutMapping("/{id}")
    public ResponseBean update(@PathVariable Long id, @RequestBody McpServerConfig config) {
        McpServerConfig existing = mcpService.findById(id);
        if (existing == null) {
            return ResponseBean.fail("MCP 服务器不存在");
        }
        config.setId(id);
        // 保留原有的启用状态，避免编辑时被覆盖
        config.setEnabled(existing.getEnabled());
        mcpService.update(config);
        return ResponseBean.success(config);
    }

    @DeleteMapping("/{id}")
    public ResponseBean delete(@PathVariable Long id) {
        McpServerConfig existing = mcpService.findById(id);
        if (existing == null) {
            return ResponseBean.fail("MCP 服务器不存在");
        }
        mcpService.deleteById(id);
        return ResponseBean.success(null);
    }

    @PutMapping("/{id}/toggle")
    public ResponseBean toggle(@PathVariable Long id) {
        McpServerConfig config = mcpService.findById(id);
        if (config == null) {
            return ResponseBean.fail("MCP 服务器不存在");
        }
        int newEnabled = (config.getEnabled() != null && config.getEnabled() == 1) ? 0 : 1;
        mcpService.setEnabled(id, newEnabled);
        return ResponseBean.success(newEnabled);
    }
}