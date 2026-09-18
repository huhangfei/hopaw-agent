package com.agent.hopaw.controller;

import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.infra.model.entity.SysConfig;
import com.agent.hopaw.infra.service.ISysConfigService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/config")
public class SysConfigController {

    private final ISysConfigService sysConfigService;

    public SysConfigController(ISysConfigService sysConfigService) {
        this.sysConfigService = sysConfigService;
    }

    @GetMapping("/{key}")
    public ResponseBean getByKey(@PathVariable String key) {
        SysConfig config = sysConfigService.getByKey(key);
        if (config == null) {
            return ResponseBean.fail("配置不存在: " + key);
        }
        return ResponseBean.success(config);
    }

    /**
     * 按需批量查询：界面只查询当前页面用到的配置项，避免全量拉取
     */
    @PostMapping("/batch")
    public ResponseBean getByKeys(@RequestBody List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return ResponseBean.success(List.of());
        }
        return ResponseBean.success(sysConfigService.getByKeys(keys));
    }

    @PostMapping
    public ResponseBean create(@RequestBody SysConfig sysConfig) {
        if (sysConfig.getConfigKey() == null || sysConfig.getConfigKey().isBlank()) {
            return ResponseBean.fail("配置键不能为空");
        }
        if (sysConfigService.getByKey(sysConfig.getConfigKey()) != null) {
            return ResponseBean.fail("配置键已存在: " + sysConfig.getConfigKey());
        }
        sysConfigService.insert(sysConfig, isEncryptRequested(sysConfig));
        return ResponseBean.success(sysConfig);
    }

    @PutMapping("/{key}")
    public ResponseBean update(@PathVariable String key, @RequestBody SysConfig sysConfig) {
        SysConfig existing = sysConfigService.getByKey(key);
        if (existing == null) {
            return ResponseBean.fail("配置不存在: " + key);
        }
        sysConfig.setConfigKey(key);
        sysConfigService.update(sysConfig, isEncryptRequested(sysConfig));
        return ResponseBean.success(sysConfig);
    }

    /**
     * 请求体 isEncrypted=1 表示要求加密保存
     */
    private boolean isEncryptRequested(SysConfig sysConfig) {
        return sysConfig.getIsEncrypted() != null && sysConfig.getIsEncrypted() == 1;
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
