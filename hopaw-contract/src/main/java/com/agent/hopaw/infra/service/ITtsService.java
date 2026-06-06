package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.model.dto.TtsVoice;

import java.util.List;

/**
 * TTS 文字转语音服务接口，支持多厂商扩展。
 */
public interface ITtsService {

    /**
     * 获取厂商编号
     */
    String getVendorCode();

    /**
     * 获取厂商名称
     */
    String getVendorName();

    /**
     * 根据配置获取可用音色列表
     * @param configJson 厂商配置 JSON（包含 appId/secret/token 等）
     */
    List<TtsVoice> listVoices(String configJson);

    /**
     * 合成语音，返回音频字节数组
     * @param configJson 厂商配置 JSON
     * @param voiceId 音色编号
     * @param text 文本内容
     * @param emotion 情感类型（可为 null，仅多情感音色有效，如 happy/angry/sad 等）
     * @return 音频字节数组（MP3 格式）
     */
    byte[] synthesize(String configJson, String voiceId, String text, String emotion);
}