package com.agent.hopaw.controller;

import com.agent.hopaw.infra.mapper.TtsConfigMapper;
import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.infra.model.dto.TtsVoice;
import com.agent.hopaw.infra.model.entity.TtsConfig;
import com.agent.hopaw.infra.service.ITtsService;
import com.agent.hopaw.infra.service.TtsServiceFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tts")
public class TtsConfigController {

    private final TtsConfigMapper ttsConfigMapper;
    private final TtsServiceFactory ttsServiceFactory;

    public TtsConfigController(TtsConfigMapper ttsConfigMapper, TtsServiceFactory ttsServiceFactory) {
        this.ttsConfigMapper = ttsConfigMapper;
        this.ttsServiceFactory = ttsServiceFactory;
    }

    /** 获取所有 TTS 配置 */
    @GetMapping("/configs")
    public ResponseBean listConfigs() {
        List<TtsConfig> configs = ttsConfigMapper.findAll();
        return ResponseBean.success(configs);
    }

    /** 获取已启用的 TTS 配置 */
    @GetMapping("/config/enabled")
    public ResponseBean getEnabledConfig(@RequestParam(defaultValue = "admin") String userId) {
        TtsConfig config = ttsConfigMapper.findEnabledByUserId(userId);
        if (config == null) {
            return ResponseBean.success(null);
        }
        return ResponseBean.success(config);
    }

    /** 保存 TTS 配置 */
    @PostMapping("/config")
    public ResponseBean saveConfig(@RequestBody TtsConfig config) {
        if (config.getVendorCode() == null || config.getVendorCode().isEmpty()) {
            return ResponseBean.fail("厂商编号不能为空");
        }
        TtsConfig existing = ttsConfigMapper.findByVendorCode(config.getVendorCode());
        if (existing != null) {
            config.setId(existing.getId());
            ttsConfigMapper.update(config);
        } else {
            ttsConfigMapper.insert(config);
        }
        return ResponseBean.success();
    }

    /** 删除 TTS 配置 */
    @DeleteMapping("/config/{id}")
    public ResponseBean deleteConfig(@PathVariable Long id) {
        ttsConfigMapper.deleteById(id);
        return ResponseBean.success();
    }

    /** 获取厂商列表 */
    @GetMapping("/vendors")
    public ResponseBean listVendors() {
        Map<String, String> vendors = ttsServiceFactory.listVendorNames();
        return ResponseBean.success(vendors);
    }

    /** 获取指定厂商的音色列表 */
    @GetMapping("/voices")
    public ResponseBean listVoices(@RequestParam String vendorCode,
                                   @RequestParam(required = false) Long configId) {
        ITtsService service = ttsServiceFactory.getService(vendorCode);
        if (service == null) {
            return ResponseBean.fail("不支持的厂商: " + vendorCode);
        }
        String configJson = "";
        if (configId != null) {
            TtsConfig config = ttsConfigMapper.findById(configId);
            if (config != null) {
                configJson = config.getConfigJson();
            }
        }
        List<TtsVoice> voices = service.listVoices(configJson);
        return ResponseBean.success(voices);
    }
}