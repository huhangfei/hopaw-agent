package com.agent.hopaw.infra.chat;

import com.agent.hopaw.infra.constant.ModelProviderEnum;
import org.springframework.stereotype.Service;

@Service
public class QianWenChatModelFactory extends  OpenAiChatModelFactory {

    @Override
    public String getProviderName() {
        return ModelProviderEnum.QWEN.getSdkName();
    }
}