package com.agent.hopaw.infra.chat;

import com.agent.hopaw.infra.constant.ModelProviderEnum;
import org.springframework.stereotype.Service;

/**
 * llama.cpp 服务暴露 OpenAI 兼容 API，直接复用 OpenAiChatModelFactory 协议。
 */
@Service
public class LlamaCppChatModelFactory extends OpenAiChatModelFactory {

    @Override
    public String getProviderName() {
        return ModelProviderEnum.LLAMACPP.getSdkName();
    }
}
