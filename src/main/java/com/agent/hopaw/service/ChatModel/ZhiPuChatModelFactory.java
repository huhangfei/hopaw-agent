package com.agent.hopaw.service.ChatModel;

import com.agent.hopaw.constant.ModelProviderEnum;
import org.springframework.stereotype.Service;

@Service
public class ZhiPuChatModelFactory extends  OpenAiChatModelFactory {

    @Override
    public String getProviderName() {
        return ModelProviderEnum.ZHIPU.getSdkName();
    }
}