package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.constant.AiModelCallSourceEnum;
import com.agent.hopaw.infra.mapper.AiModelMapper;
import com.agent.hopaw.infra.monitor.LangChain4jChatModelListener;
import com.agent.hopaw.infra.model.entity.*;
import com.agent.hopaw.infra.model.dto.*;
import com.agent.hopaw.infra.chat.ChatModelFactory;
import com.alibaba.fastjson2.JSON;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AiModelService implements IAiModelService {
    private static final Logger log = LoggerFactory.getLogger(AiModelService.class);
    private final AiModelMapper aiModelMapper;
    private final AiModelProviderService aiModelProviderService;
    private final IChatModelListenerProvider chatModelListenerProvider;

    private final Map<String, ChatModelFactory> factories = new HashMap<>();
    public AiModelService(AiModelMapper aiModelMapper, AiModelProviderService aiModelProviderService, List<ChatModelFactory> factoryList, IChatModelListenerProvider chatModelListenerProvider) {
        this.aiModelMapper = aiModelMapper;
        this.aiModelProviderService = aiModelProviderService;
        this.chatModelListenerProvider = chatModelListenerProvider;
        for (ChatModelFactory factory : factoryList) {
            factories.put(factory.getProviderName().toLowerCase(), factory);
        }
    }
    public AiModel findById(Long id) {
        AiModel aiModel = aiModelMapper.findById(id);
        return aiModel;
    }


    public List<AiModel> findByProviderId(Long providerId) {
        return aiModelMapper.findByProviderId(providerId);
    }

    public int insert(AiModel aiModel) {
        testAndSetCapabilities(aiModel);
        return aiModelMapper.insert(aiModel);
    }

    public int update(AiModel aiModel) {
        testAndSetCapabilities(aiModel);
        return aiModelMapper.update(aiModel);
    }

    public int deleteById(Long id) {
        return aiModelMapper.deleteById(id);
    }

    public ModelCapabilityTestResult testModel(Long id) {
        AiModel aiModel = aiModelMapper.findById(id);
        if (aiModel == null) {
            throw new IllegalArgumentException("模型不存在: " + id);
        }
        ModelCapabilityTestResult result = testAndSetCapabilities(aiModel);
        aiModelMapper.update(aiModel);
        return result;
    }

    private ModelCapabilityTestResult testAndSetCapabilities(AiModel aiModel) {
        try {
            AiModelProvider provider = aiModelProviderService.findById(aiModel.getProviderId());
            if (provider == null) {
                log.warn("未找到模型提供商 providerId={}", aiModel.getProviderId());
                return new ModelCapabilityTestResult(false, java.util.Collections.emptyList(), "未找到模型提供商");
            }
            if (provider.getUrl() == null || provider.getUrl().isBlank()) {
                log.warn("模型提供商 URL 未配置 providerId={}", aiModel.getProviderId());
                return new ModelCapabilityTestResult(false, java.util.Collections.emptyList(), "API 地址未配置，请先设置提供商地址");
            }
            if (provider.getApiKey() == null || provider.getApiKey().isBlank()) {
                log.warn("模型提供商 API 密钥未配置 providerId={}", aiModel.getProviderId());
                return new ModelCapabilityTestResult(false, java.util.Collections.emptyList(), "API 密钥未配置，请先设置密钥");
            }
            AiModelVO aiModelVO = new AiModelVO();
            org.springframework.beans.BeanUtils.copyProperties(aiModel, aiModelVO);
            aiModelVO.setAiModelProvider(provider);
            ChatModelFactory factory = factories.get(aiModelVO.getAiModelProvider().getSdkName().toLowerCase());
            ChatModelListener chatModelListener = chatModelListenerProvider.getChatModelListener(AiModelCallSourceEnum.ModelTEST, null, null, null);
            ChatModel chatModel = factory.createChatModel(aiModelVO, false, chatModelListener);
            ModelCapabilityTestResult result = factory.testModelCapability(chatModel);

            log.info("模型能力测试结果 [{}]: {}", aiModel.getModelName(), result.getMessage());

            if (result.isVerified()) {
                aiModel.setCapabilities(result.getCapabilities().stream()
                        .map(cap -> cap.getCode())
                        .collect(Collectors.joining(",")));
                aiModel.setVerified(true);
            } else {
                aiModel.setVerified(false);
            }
            return result;
        } catch (Exception e) {
            log.error("测试模型能力失败: modelName={}", aiModel.getModelName(), e);
            aiModel.setVerified(false);
            return new ModelCapabilityTestResult(false, java.util.Collections.emptyList(), "测试异常：" + e.getMessage());
        }
    }

    public AiModelVO findAiModelVOById(Long id) {
        AiModel aiModel = aiModelMapper.findById(id);
        if (aiModel == null) {
            throw new IllegalStateException("Unknown API model: " + id );
        }
        AiModelProvider provider = aiModelProviderService.findById(aiModel.getProviderId());
        if (provider == null) {
            throw new IllegalStateException("Unknown API provider: " + aiModel.getProviderId() +
                    ". Available providers: " + factories.keySet());
        }
        AiModelVO aiModelVO = new AiModelVO();
        org.springframework.beans.BeanUtils.copyProperties(aiModel, aiModelVO);
        aiModelVO.setAiModelProvider(provider);
        return aiModelVO;
    }

    public ChatModel createChatModel(Long aiModelId,boolean enableThinking, ChatModelListener chatModelListener) {
        AiModelVO aiModelVO = findAiModelVOById(aiModelId);
        ChatModelFactory chatModelFactory = factories.get(aiModelVO.getAiModelProvider().getSdkName().toLowerCase());
        return chatModelFactory.createChatModel(aiModelVO, enableThinking, chatModelListener);
    }

    public StreamingChatModel createStreamingChatModel(Long aiModelId,boolean enableThinking, ChatModelListener chatModelListener) {
        AiModelVO aiModelVO = findAiModelVOById(aiModelId);
        ChatModelFactory chatModelFactory = factories.get(aiModelVO.getAiModelProvider().getSdkName().toLowerCase());
        return chatModelFactory.createStreamingChatModel(aiModelVO, enableThinking, chatModelListener);
    }

    public Map<String, ChatModelFactory> getAllFactories() {
        return new HashMap<>(factories);
    }



    public String getDefaultAiModelExtParamsJson(){
        AiModelExtParams aiModelExtParams = new AiModelExtParams("reasoning_content", true, true, "high", 0.5, 30L, false, false,false);
        return JSON.toJSONString(aiModelExtParams);
    }


}
