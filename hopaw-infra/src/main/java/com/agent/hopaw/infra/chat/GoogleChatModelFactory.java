package com.agent.hopaw.infra.chat;

import com.agent.hopaw.infra.constant.ModelProviderEnum;
import org.springframework.stereotype.Service;

@Service
public class GoogleChatModelFactory extends  OpenAiChatModelFactory {

    @Override
    public String getProviderName() {
        return ModelProviderEnum.GOOGLE.getSdkName();
    }
}