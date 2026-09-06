package com.agent.hopaw.infra.chat;

import com.agent.hopaw.infra.constant.ModelProviderEnum;
import org.springframework.stereotype.Service;

/**
 * DeepSeek 工厂：接口兼容 OpenAI 协议，行为差异全部由扩展参数（extParams）控制，
 * 直接复用 OpenAI 实现，仅覆写提供商名称
 */
@Service
public class DeepSeekChatModelFactory extends OpenAiChatModelFactory {

    @Override
    public String getProviderName() {
        return ModelProviderEnum.DEEPSEEK.getSdkName();
    }
}
