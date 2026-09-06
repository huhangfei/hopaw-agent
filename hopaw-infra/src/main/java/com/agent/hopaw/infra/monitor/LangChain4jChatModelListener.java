package com.agent.hopaw.infra.monitor;

import com.agent.hopaw.infra.constant.AiModelCallSourceEnum;
import com.agent.hopaw.infra.event.TokenUsageEvent;
import com.agent.hopaw.infra.model.entity.RequestResponseLog;
import com.agent.hopaw.infra.service.IRequestResponseLogService;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 监控
 * @author hhf
 */
public class LangChain4jChatModelListener implements ChatModelListener {

    private static final Logger logger = LoggerFactory.getLogger(LangChain4jChatModelListener.class);

    /** attributes 中存放本次调用开始时间的 key（请求/响应/错误上下文共享同一 attributes Map） */
    private static final String ATTR_START_TIME_MS = LangChain4jChatModelListener.class.getName() + ".startTimeMs";

    public LangChain4jChatModelListener(AiModelCallSourceEnum source) {
        this.source = source;
    }

    /**
     * 来源
     */
    private AiModelCallSourceEnum source;
    private String sessionId;
    /**
     * 智能体Id
     */
    private Long agentId;
    /**
     * 用户编号
     */
    private String userId;
    /**
     * 请求编号
     */
    private String requestId;
    /**
     * 扩展参数
     */
    private Map<String,Object> exData;
    /**
     * 请求响应日志服务：可选，未设置时跳过落库
     */
    private IRequestResponseLogService requestResponseLogService;
    public LangChain4jChatModelListener setExData(Map<String,Object> exData) {
        this.exData = exData;
        return this;
    }

    public LangChain4jChatModelListener setAgentId(Long agentId) {
        this.agentId = agentId;
        return this;
    }

    public LangChain4jChatModelListener setUserId(String userId) {
        this.userId = userId;
        return this;
    }

    public LangChain4jChatModelListener setRequestId(String requestId) {
        this.requestId = requestId;
        return this;
    }

    public LangChain4jChatModelListener setRequestResponseLogService(IRequestResponseLogService requestResponseLogService) {
        this.requestResponseLogService = requestResponseLogService;
        return this;
    }

    public LangChain4jChatModelListener setEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
        return this;
    }

    private ApplicationEventPublisher eventPublisher;

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        // 记录开始时间到共享 attributes，响应/错误时计算耗时
        requestContext.attributes().put(ATTR_START_TIME_MS, System.currentTimeMillis());

        ChatRequest chatRequest = requestContext.chatRequest();
        logger.debug("========== LangChain4j 请求开始 ==========");
        logger.debug("模型: {}", chatRequest.parameters().modelName());

        if (chatRequest.messages() != null) {
            for (ChatMessage message : chatRequest.messages()) {
                if (message instanceof UserMessage) {
                    UserMessage userMessage=((UserMessage) message);

                    for (Content content : userMessage.contents()) {
                        if(content instanceof TextContent){
                            logger.debug("用户消息 [User][{}]: {}", userMessage.name(),((TextContent)content).text());
                        }else  if(content instanceof ImageContent){
                            logger.debug("用户消息 [User][{}]: {}", userMessage.name(),"Image");
                        }else  if(content instanceof VideoContent){
                            logger.debug("用户消息 [User][{}]: {}", userMessage.name(),"Video");
                        }else  if(content instanceof AudioContent){
                            logger.debug("用户消息 [User][{}]: {}", userMessage.name(),"Audio");
                        }else  if(content instanceof PdfFileContent){
                            logger.debug("用户消息 [User][{}]: {}", userMessage.name(),"PdfFile");
                        }else{
                            logger.debug("用户消息 [User][{}]: {}", userMessage.name(),"未知");
                        }
                    }
                } else if (message instanceof AiMessage) {
                    AiMessage aiMessage = (AiMessage) message;
                    if(aiMessage.thinking()!=null){
                        logger.debug("助手消息 [Ai thinking]: {}", aiMessage.thinking());
                    } if(aiMessage.text()!=null){
                        logger.debug("助手消息 [Ai text]: {}", aiMessage.text());
                    }
                    if(aiMessage.toolExecutionRequests()!=null && !aiMessage.toolExecutionRequests().isEmpty()){
                        logger.debug("助手消息 [Ai toolExecutionRequests]: {}", aiMessage.toolExecutionRequests().stream().map(x->x.id()+" "+x.name()+" "+x.arguments()).collect(Collectors.joining(",")));
                    }
                } else if (message instanceof SystemMessage) {
                    logger.debug("系统消息 [System]: {}", ((SystemMessage) message).text());
                } else if (message instanceof ToolExecutionResultMessage) {
                    ToolExecutionResultMessage toolExecutionResultMessage = (ToolExecutionResultMessage) message;
                    logger.debug("工具执行结果 [Tool][{}][{}]: {}",toolExecutionResultMessage.toolName(),toolExecutionResultMessage.id(), toolExecutionResultMessage.text());
                } else {
                    logger.debug("其他消息 [{}]: {}", message.getClass().getSimpleName(), message);
                }
            }
        }
        logger.debug("=================LangChain4j 请求结束=========================");
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        ChatRequest chatRequest = responseContext.chatRequest();
        ChatResponse response = responseContext.chatResponse();
        ChatResponseMetadata metadata = response.metadata();
        logger.debug("========== LangChain4j 响应完成 ==========");
        logger.debug("模型: {}", metadata.modelName());
        if (response.aiMessage() != null) {
            logger.debug("助手回复:thinking {}", response.aiMessage().thinking());
            logger.debug("助手回复:text {}", response.aiMessage().text());
            logger.debug("助手回复:toolExecutionRequests {}", response.aiMessage().toolExecutionRequests().stream().map(x->x.id()+" "+x.name()+" "+x.arguments()).collect(Collectors.joining(",")));
        }

        dev.langchain4j.model.output.TokenUsage tokenUsage = metadata.tokenUsage();
        if (tokenUsage != null) {
            logger.debug("Token 用量:");
            logger.debug("  - 输入 tokens: {}", tokenUsage.inputTokenCount());
            logger.debug("  - 输出 tokens: {}", tokenUsage.outputTokenCount());
            logger.debug("  - 总 tokens: {}", tokenUsage.totalTokenCount());

            if(eventPublisher!=null){
                try {
                    TokenUsageEvent message = new TokenUsageEvent(
                            agentId,
                            metadata.modelName(),
                            tokenUsage.inputTokenCount(),
                            tokenUsage.outputTokenCount(),
                            tokenUsage.totalTokenCount(),
                            userId,
                            sessionId,
                            source.getValue(),
                            LocalDateTime.now()
                    );
                    message.setExData(exData);
                    eventPublisher.publishEvent(message);
                } catch (Exception e) {
                    logger.error("发布 Token 用量消息失败", e);
                }
            }
        }
        logger.debug("==========================================");

        saveLog(chatRequest, buildResponseJson(response), null, tokenUsage, responseContext.attributes().get(ATTR_START_TIME_MS));
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        logger.error("========== LangChain4j 请求错误 ==========");
        logger.error("错误: "+ errorContext.error().getMessage(),errorContext.error());
        logger.error("==========================================");

        saveLog(errorContext.chatRequest(), null,
                errorContext.error() == null ? "未知错误" : errorContext.error().toString(),
                null, errorContext.attributes().get(ATTR_START_TIME_MS));
    }

    /**
     * 构建请求日志 JSON：模型、参数摘要、完整消息列表（含各类型消息原文）
     */
    private String buildRequestJson(ChatRequest chatRequest) {
        try {
            JSONObject json = new JSONObject();
            if (chatRequest.parameters() != null) {
                json.put("modelName", chatRequest.parameters().modelName());
                json.put("temperature", chatRequest.parameters().temperature());
                json.put("topP", chatRequest.parameters().topP());
                json.put("maxOutputTokens", chatRequest.parameters().maxOutputTokens());
            }
            JSONArray messages = new JSONArray();
            if (chatRequest.messages() != null) {
                for (ChatMessage message : chatRequest.messages()) {
                    JSONObject msgJson = new JSONObject();
                    msgJson.put("type", message.type() == null ? null : message.type().toString());
                    try {
                        msgJson.put("content", JSONObject.parse(ChatMessageSerializer.messageToJson(message)));
                    } catch (Exception e) {
                        msgJson.put("content", String.valueOf(message));
                    }
                    messages.add(msgJson);
                }
            }
            json.put("messageCount", messages.size());
            json.put("messages", messages);
            return json.toJSONString();
        } catch (Exception e) {
            logger.warn("构建请求日志 JSON 失败", e);
            return null;
        }
    }

    /**
     * 构建响应日志 JSON：模型、思考、正文、工具调用、Token 用量
     */
    private String buildResponseJson(ChatResponse response) {
        try {
            JSONObject json = new JSONObject();
            ChatResponseMetadata metadata = response.metadata();
            if (metadata != null) {
                json.put("modelName", metadata.modelName());
                json.put("finishReason", metadata.finishReason() == null ? null : metadata.finishReason().toString());
                TokenUsage usage = metadata.tokenUsage();
                if (usage != null) {
                    JSONObject usageJson = new JSONObject();
                    usageJson.put("inputTokens", usage.inputTokenCount());
                    usageJson.put("outputTokens", usage.outputTokenCount());
                    usageJson.put("totalTokens", usage.totalTokenCount());
                    json.put("tokenUsage", usageJson);
                }
            }
            AiMessage aiMessage = response.aiMessage();
            if (aiMessage != null) {
                json.put("thinking", aiMessage.thinking());
                json.put("text", aiMessage.text());
                if (aiMessage.toolExecutionRequests() != null && !aiMessage.toolExecutionRequests().isEmpty()) {
                    JSONArray tools = new JSONArray();
                    for (ToolExecutionRequest request : aiMessage.toolExecutionRequests()) {
                        JSONObject tool = new JSONObject();
                        tool.put("id", request.id());
                        tool.put("name", request.name());
                        tool.put("arguments", request.arguments());
                        tools.add(tool);
                    }
                    json.put("toolExecutionRequests", tools);
                }
            }
            return json.toJSONString();
        } catch (Exception e) {
            logger.warn("构建响应日志 JSON 失败", e);
            return null;
        }
    }

    /**
     * 落库一条请求响应日志：服务未设置或落库异常时仅记日志，不影响主流程
     */
    private void saveLog(ChatRequest chatRequest, String responseJson, String errorText,
                         dev.langchain4j.model.output.TokenUsage tokenUsage, Object startTimeObj) {
        if (requestResponseLogService == null) {
            return;
        }
        try {
            RequestResponseLog log = new RequestResponseLog();
            log.setSessionId(sessionId);
            log.setRequestId(requestId);
            log.setUserId(userId);
            log.setAgentId(agentId);
            log.setModelName(chatRequest != null && chatRequest.parameters() != null
                    ? chatRequest.parameters().modelName() : null);
            log.setSource(source.getDescription());
            log.setRequestJson(buildRequestJson(chatRequest));
            log.setResponseJson(responseJson);
            log.setErrorText(errorText);
            log.setStatus(errorText == null ? "success" : "error");
            if (tokenUsage != null) {
                log.setInputTokens(tokenUsage.inputTokenCount());
                log.setOutputTokens(tokenUsage.outputTokenCount());
                log.setTotalTokens(tokenUsage.totalTokenCount());
            }
            if (startTimeObj instanceof Long) {
                log.setCostMs(Math.max(0, System.currentTimeMillis() - (Long) startTimeObj));
            }
            log.setCreateTime(LocalDateTime.now());
            requestResponseLogService.saveLog(log);
        } catch (Exception e) {
            logger.error("保存请求响应日志失败", e);
        }
    }

    public String getSessionId() {
        return sessionId;
    }

    public LangChain4jChatModelListener setSessionId(String sessionId) {
        this.sessionId = sessionId;
        return this;
    }
}
