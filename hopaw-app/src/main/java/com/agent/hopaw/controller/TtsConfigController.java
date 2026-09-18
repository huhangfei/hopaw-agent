package com.agent.hopaw.controller;

import com.agent.hopaw.infra.constant.TtsEmotionEnum;
import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.infra.model.dto.TtsVoice;
import com.agent.hopaw.infra.model.entity.TtsConfig;
import com.agent.hopaw.infra.service.ITtsService;
import com.agent.hopaw.infra.service.TtsConfigService;
import com.agent.hopaw.infra.service.TtsServiceFactory;
import com.agent.hopaw.infra.service.TtsVoiceService;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tts")
public class TtsConfigController {

    private final TtsConfigService ttsConfigService;
    private final TtsServiceFactory ttsServiceFactory;
    private final TtsVoiceService ttsVoiceService;

    public TtsConfigController(TtsConfigService ttsConfigService,
                               TtsServiceFactory ttsServiceFactory,
                               TtsVoiceService ttsVoiceService) {
        this.ttsConfigService = ttsConfigService;
        this.ttsServiceFactory = ttsServiceFactory;
        this.ttsVoiceService = ttsVoiceService;
    }

    /** 获取所有 TTS 配置（configJson 解密后返回，附各渠道音色数，供前端展示与编辑） */
    @GetMapping("/configs")
    public ResponseBean listConfigs() {
        List<TtsConfig> configs = ttsConfigService.findAll();
        for (TtsConfig config : configs) {
            config.setVoiceCount(ttsVoiceService.countByConfigId(config.getId()));
        }
        return ResponseBean.success(configs);
    }

    /** 获取所有已启用的 TTS 配置（供虚拟形象选择） */
    @GetMapping("/configs/enabled")
    public ResponseBean listEnabledConfigs() {
        List<TtsConfig> configs = ttsConfigService.findAllEnabled();
        return ResponseBean.success(configs);
    }

    /** 获取已启用的 TTS 配置 */
    @GetMapping("/config/enabled")
    public ResponseBean getEnabledConfig(@RequestParam(defaultValue = "admin") String userId) {
        TtsConfig config = ttsConfigService.findEnabledByUserId(userId);
        if (config == null) {
            return ResponseBean.success(null);
        }
        return ResponseBean.success(config);
    }

    /**
     * 保存 TTS 配置（新增或更新）。同厂商可添加多条配置。configJson 加密后落库。
     * 新增配置时自动复制厂商实现的默认音色到该渠道。
     */
    @PostMapping("/config")
    public ResponseBean saveConfig(@RequestBody TtsConfig config) {
        if (config.getVendorCode() == null || config.getVendorCode().isEmpty()) {
            return ResponseBean.fail("厂商编号不能为空");
        }
        boolean isNew = config.getId() == null;
        ttsConfigService.save(config);
        if (isNew) {
            ttsVoiceService.initDefaultVoices(config.getId(), config.getVendorCode());
        }
        return ResponseBean.success();
    }

    /** 删除 TTS 配置（同时删除该渠道的音色） */
    @DeleteMapping("/config/{id}")
    public ResponseBean deleteConfig(@PathVariable Long id) {
        ttsConfigService.deleteById(id);
        ttsVoiceService.clearByConfigId(id);
        return ResponseBean.success();
    }

    /** 获取厂商列表 */
    @GetMapping("/vendors")
    public ResponseBean listVendors() {
        Map<String, String> vendors = ttsServiceFactory.listVendorNames();
        return ResponseBean.success(vendors);
    }

    /**
     * 获取音色列表：优先按 configId 查询该渠道已配置的音色（存储于 tts_voice 表）；
     * 未传 configId 时返回厂商实现的默认音色（兼容旧调用）。
     */
    @GetMapping("/voices")
    public ResponseBean listVoices(@RequestParam String vendorCode,
                                   @RequestParam(required = false) Long configId) {
        ITtsService service = ttsServiceFactory.getService(vendorCode);
        if (service == null) {
            return ResponseBean.fail("不支持的厂商: " + vendorCode);
        }
        if (configId != null) {
            TtsConfig config = ttsConfigService.findById(configId);
            if (config == null) {
                return ResponseBean.fail("TTS 配置不存在: " + configId);
            }
            // 兼容旧配置：渠道尚无音色数据时自动复制厂商默认音色，避免空列表
            if (ttsVoiceService.countByConfigId(configId) == 0) {
                ttsVoiceService.initDefaultVoices(configId, config.getVendorCode());
            }
            return ResponseBean.success(ttsVoiceService.listByConfigId(configId));
        }
        return ResponseBean.success(service.listVoices(""));
    }

    /** 查询某渠道的音色列表 */
    @GetMapping("/config/{configId}/voices")
    public ResponseBean listConfigVoices(@PathVariable Long configId) {
        TtsConfig config = ttsConfigService.findById(configId);
        if (config == null) {
            return ResponseBean.fail("TTS 配置不存在: " + configId);
        }
        // 兼容旧配置：渠道尚无音色数据时自动复制厂商默认音色
        if (ttsVoiceService.countByConfigId(configId) == 0) {
            ttsVoiceService.initDefaultVoices(configId, config.getVendorCode());
        }
        return ResponseBean.success(ttsVoiceService.listByConfigId(configId));
    }

    /** 全量保存某渠道的音色（前端弹框增删改后整体提交） */
    @PutMapping("/config/{configId}/voices")
    public ResponseBean saveConfigVoices(@PathVariable Long configId,
                                         @RequestBody List<TtsVoice> voices) {
        int count = ttsVoiceService.saveAll(configId, voices);
        return ResponseBean.success(Map.of("count", count));
    }

    /** 重置某渠道的音色为厂商实现的默认音色 */
    @PostMapping("/config/{configId}/voices/reset")
    public ResponseBean resetConfigVoices(@PathVariable Long configId) {
        TtsConfig config = ttsConfigService.findById(configId);
        if (config == null) {
            return ResponseBean.fail("TTS 配置不存在: " + configId);
        }
        int count = ttsVoiceService.initDefaultVoices(configId, config.getVendorCode());
        return ResponseBean.success(Map.of("count", count));
    }

    /**
     * 测试渠道配置：用指定音色合成文本，返回 base64 音频供前端播放。
     */
    @PostMapping("/config/{configId}/test")
    public ResponseBean testSynthesize(@PathVariable Long configId,
                                       @RequestBody TtsTestRequest request) {
        TtsConfig config = ttsConfigService.findById(configId);
        if (config == null) {
            return ResponseBean.fail("TTS 配置不存在: " + configId);
        }
        if (request.getVoiceId() == null || request.getVoiceId().isBlank()) {
            return ResponseBean.fail("请选择音色");
        }
        if (request.getText() == null || request.getText().isBlank()) {
            return ResponseBean.fail("测试文本不能为空");
        }
        ITtsService service = ttsServiceFactory.getService(config.getVendorCode());
        if (service == null) {
            return ResponseBean.fail("不支持的厂商: " + config.getVendorCode());
        }
        byte[] audio = service.synthesize(config.getConfigJson(),
                request.getVoiceId().trim(), request.getText().trim(),
                TtsEmotionEnum.fromCode(request.getEmotion()));
        if (audio == null || audio.length == 0) {
            return ResponseBean.fail("合成失败，请检查厂商配置是否正确");
        }
        String format = isWav(audio) ? "wav" : "mp3";
        return ResponseBean.success(Map.of(
                "audio", Base64.getEncoder().encodeToString(audio),
                "format", format,
                "bytes", audio.length
        ));
    }

    /** 检测 WAV 文件头（RIFF....WAVE） */
    private static boolean isWav(byte[] a) {
        return a.length >= 12
                && a[0] == 'R' && a[1] == 'I' && a[2] == 'F' && a[3] == 'F'
                && a[8] == 'W' && a[9] == 'A' && a[10] == 'V' && a[11] == 'E';
    }

    /** TTS 测试请求体 */
    public static class TtsTestRequest {
        private String voiceId;
        private String text;
        private String emotion;

        public String getVoiceId() {
            return voiceId;
        }

        public void setVoiceId(String voiceId) {
            this.voiceId = voiceId;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public String getEmotion() {
            return emotion;
        }

        public void setEmotion(String emotion) {
            this.emotion = emotion;
        }
    }
}
