package com.agent.hopaw.service.ChatModel;

import com.agent.hopaw.constant.ModelProviderEnum;
import com.agent.hopaw.service.LangChain4jMonitoringService;
import org.springframework.stereotype.Service;

@Service
public class ZhiPuChatModelFactory extends  OpenAiChatModelFactory {

    public ZhiPuChatModelFactory(LangChain4jMonitoringService monitoringService) {
        super(monitoringService);
    }

    @Override
    public String getProviderName() {
        return ModelProviderEnum.ZHIPU.getSdkName();
    }
}