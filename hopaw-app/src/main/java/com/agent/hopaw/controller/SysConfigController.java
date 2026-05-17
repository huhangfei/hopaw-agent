package com.agent.hopaw.controller;

import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.infra.model.entity.SysConfig;
import com.agent.hopaw.infra.service.SysConfigService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/config")
public class SysConfigController {

    private final SysConfigService sysConfigService;

    public SysConfigController(SysConfigService sysConfigService) {
        this.sysConfigService = sysConfigService;
    }

    @GetMapping
    public ResponseBean getAll() {
        List<SysConfig> list = sysConfigService.getAll();
        return ResponseBean.success(list);
    }

    @GetMapping("/{key}")
    public ResponseBean getByKey(@PathVariable String key) {
        SysConfig config = sysConfigService.getByKey(key);
        if (config == null) {
            return ResponseBean.fail("配置不存在: " + key);
        }
        return ResponseBean.success(config);
    }

    @PostMapping
    public ResponseBean create(@RequestBody SysConfig sysConfig) {
        if (sysConfig.getConfigKey() == null || sysConfig.getConfigKey().isBlank()) {
            return ResponseBean.fail("配置键不能为空");
        }
        if (sysConfigService.getByKey(sysConfig.getConfigKey()) != null) {
            return ResponseBean.fail("配置键已存在: " + sysConfig.getConfigKey());
        }
        sysConfigService.save(sysConfig);
        return ResponseBean.success(sysConfig);
    }

    @PutMapping("/{key}")
    public ResponseBean update(@PathVariable String key, @RequestBody SysConfig sysConfig) {
        SysConfig existing = sysConfigService.getByKey(key);
        if (existing == null) {
            return ResponseBean.fail("配置不存在: " + key);
        }
        sysConfig.setConfigKey(key);
        sysConfigService.update(sysConfig);
        return ResponseBean.success(sysConfig);
    }

    @DeleteMapping("/{key}")
    public ResponseBean delete(@PathVariable String key) {
        SysConfig existing = sysConfigService.getByKey(key);
        if (existing == null) {
            return ResponseBean.fail("配置不存在: " + key);
        }
        sysConfigService.deleteByKey(key);
        return ResponseBean.success();
    }
}
