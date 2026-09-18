package com.agent.hopaw.infra.chat;

import com.agent.hopaw.infra.constant.ModelProviderEnum;
import com.agent.hopaw.infra.model.dto.AiModelVO;
import com.agent.hopaw.infra.model.entity.AiModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class OllamaChatModelFactory extends BaseChatModelFactory {

    @Override
    public ChatModel createChatModel(AiModelVO aiModel) {
        return createChatModel(aiModel, null, null, null);
    }

    @Override
    public ChatModel createChatModel(AiModelVO aiModel, ChatModelListener langChain4JMonitor) {
        return createChatModel(aiModel, null, null, langChain4JMonitor);
    }

    @Override
    public ChatModel createChatModel(AiModelVO aiModel, Boolean enableThinking, String reasoningEffort, ChatModelListener monitoringService) {
        AiModelProvider p = aiModel.getAiModelProvider();
        if (enableThinking == null) enableThinking = getEnableThinking(aiModel);
        enableThinking = constrainEnableThinking(aiModel, enableThinking);
        Boolean think = enableThinking;
        Boolean retThink = enableThinking;
        Duration timeout = Duration.ofSeconds(getTimeoutSeconds(aiModel));

        var b = OllamaChatModel.builder();
        b.baseUrl(p.getUrl());
        b.modelName(aiModel.getModelName());
        b.temperature(getTemperature(aiModel));
        b.numPredict(getOutputMaxTokens(aiModel));
        b.think(think);
        b.returnThinking(retThink);
        b.logRequests(getLogRequests(aiModel));
        b.logResponses(getLogResponses(aiModel));
        b.timeout(timeout);
        applyChatParams(b, aiModel);
        if (monitoringService != null) b.listeners(List.of(monitoringService));
        return b.build();
    }

    @Override
    public StreamingChatModel createStreamingChatModel(AiModelVO aiModel) {
        return createStreamingChatModel(aiModel, null, null, null);
    }

    @Override
    public StreamingChatModel createStreamingChatModel(AiModelVO aiModel, ChatModelListener langChain4JMonitor) {
        return createStreamingChatModel(aiModel, null, null, langChain4JMonitor);
    }

    @Override
    public StreamingChatModel createStreamingChatModel(AiModelVO aiModel, Boolean enableThinking, String reasoningEffort, ChatModelListener monitoringService) {
        AiModelProvider p = aiModel.getAiModelProvider();
        if (enableThinking == null) enableThinking = getEnableThinking(aiModel);
        enableThinking = constrainEnableThinking(aiModel, enableThinking);
        Boolean think = enableThinking;
        Boolean retThink = enableThinking;
        Duration timeout = Duration.ofSeconds(getTimeoutSeconds(aiModel));

        var b = OllamaStreamingChatModel.builder();
        b.baseUrl(p.getUrl());
        b.modelName(aiModel.getModelName());
        b.temperature(getTemperature(aiModel));
        b.numPredict(getOutputMaxTokens(aiModel));
        b.think(think);
        b.returnThinking(retThink);
        b.logRequests(getLogRequests(aiModel));
        b.logResponses(getLogResponses(aiModel));
        b.timeout(timeout);
        applyStreamParams(b, aiModel);
        if (monitoringService != null) b.listeners(List.of(monitoringService));
        return b.build();
    }

    // ==================== Ollama 特有参数 ====================
    // extParams: numCtx, topK, topP, minP, repeatPenalty, repeatLastN,
    //            seed, mirostat, mirostatEta, mirostatTau, stop

    private void applyChatParams(OllamaChatModel.OllamaChatModelBuilder builder, AiModelVO aiModel) {
        Integer numCtx = getInt(aiModel, "numCtx");
        Integer topK = getInt(aiModel, "topK");
        Double topP = getDouble(aiModel, "topP");
        Double minP = getDouble(aiModel, "minP");
        Double repeatPenalty = getDouble(aiModel, "repeatPenalty");
        Integer repeatLastN = getInt(aiModel, "repeatLastN");
        Integer seed = getInt(aiModel, "seed");
        Integer mirostat = getInt(aiModel, "mirostat");
        Double mirostatEta = getDouble(aiModel, "mirostatEta");
        Double mirostatTau = getDouble(aiModel, "mirostatTau");
        String stop = getString(aiModel, "stop");
        if (numCtx != null) builder.numCtx(numCtx);
        if (topK != null) builder.topK(topK);
        if (topP != null) builder.topP(topP);
        if (minP != null) builder.minP(minP);
        if (repeatPenalty != null) builder.repeatPenalty(repeatPenalty);
        if (repeatLastN != null) builder.repeatLastN(repeatLastN);
        if (seed != null) builder.seed(seed);
        if (mirostat != null) builder.mirostat(mirostat);
        if (mirostatEta != null) builder.mirostatEta(mirostatEta);
        if (mirostatTau != null) builder.mirostatTau(mirostatTau);
        if (stop != null && !stop.isEmpty()) builder.stop(List.of(stop.split(",")));
    }

    private void applyStreamParams(OllamaStreamingChatModel.OllamaStreamingChatModelBuilder builder, AiModelVO aiModel) {
        Integer numCtx = getInt(aiModel, "numCtx");
        Integer topK = getInt(aiModel, "topK");
        Double topP = getDouble(aiModel, "topP");
        Double minP = getDouble(aiModel, "minP");
        Double repeatPenalty = getDouble(aiModel, "repeatPenalty");
        Integer repeatLastN = getInt(aiModel, "repeatLastN");
        Integer seed = getInt(aiModel, "seed");
        Integer mirostat = getInt(aiModel, "mirostat");
        Double mirostatEta = getDouble(aiModel, "mirostatEta");
        Double mirostatTau = getDouble(aiModel, "mirostatTau");
        String stop = getString(aiModel, "stop");
        if (numCtx != null) builder.numCtx(numCtx);
        if (topK != null) builder.topK(topK);
        if (topP != null) builder.topP(topP);
        if (minP != null) builder.minP(minP);
        if (repeatPenalty != null) builder.repeatPenalty(repeatPenalty);
        if (repeatLastN != null) builder.repeatLastN(repeatLastN);
        if (seed != null) builder.seed(seed);
        if (mirostat != null) builder.mirostat(mirostat);
        if (mirostatEta != null) builder.mirostatEta(mirostatEta);
        if (mirostatTau != null) builder.mirostatTau(mirostatTau);
        if (stop != null && !stop.isEmpty()) builder.stop(List.of(stop.split(",")));
    }

    private Integer getInt(AiModelVO m, String k) {
        Object v = ep(m, k);
        return v instanceof Number ? ((Number) v).intValue() : null;
    }

    private Double getDouble(AiModelVO m, String k) {
        Object v = ep(m, k);
        return v instanceof Number ? ((Number) v).doubleValue() : null;
    }

    private String getString(AiModelVO m, String k) {
        Object v = ep(m, k);
        return v != null ? v.toString() : null;
    }

    private Object ep(AiModelVO m, String p) {
        if (m.getExtParams() != null) {
            var j = com.alibaba.fastjson2.JSON.parseObject(m.getExtParams());
            if (j != null && j.containsKey(p)) return j.get(p);
        }
        if (m.getAiModelProvider() != null && m.getAiModelProvider().getExtParams() != null) {
            var j = com.alibaba.fastjson2.JSON.parseObject(m.getAiModelProvider().getExtParams());
            if (j != null && j.containsKey(p)) return j.get(p);
        }
        return null;
    }

    @Override
    public String getProviderName() {
        return ModelProviderEnum.OLLAMA.getSdkName();
    }
}
