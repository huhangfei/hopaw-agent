package com.agent.hopaw.service;

import com.agent.hopaw.constant.AiModelCallSourceEnum;
import com.alibaba.fastjson2.JSON;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * 监控
 */
public class LangChain4jMonitor implements ChatModelListener {

    private static final Logger logger = LoggerFactory.getLogger(LangChain4jMonitor.class);

    public LangChain4jMonitor(AiModelCallSourceEnum source) {
        this.source = source;
    }

    /**
     * 来源
     */
    private AiModelCallSourceEnum source;
    /**
     * 智能体Id
     */
    private Long agentId;
    /**
     * 用户编号
     */
    private String userId;

    public LangChain4jMonitor setTokenUsageService(TokenUsageService tokenUsageService) {
        this.tokenUsageService = tokenUsageService;
        return this;
    }

    private  TokenUsageService tokenUsageService;

    public LangChain4jMonitor setAgentId(Long agentId) {
        this.agentId = agentId;
        return this;
    }

    public LangChain4jMonitor setUserId(String userId) {
        this.userId = userId;
        return this;
    }

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {

        ChatRequest chatRequest = requestContext.chatRequest();
        logger.info("========== LangChain4j 请求开始 ==========");
        logger.info("模型: {}", chatRequest.parameters().modelName());

        if (chatRequest.messages() != null) {
            for (ChatMessage message : chatRequest.messages()) {
                if (message instanceof UserMessage) {
                    UserMessage userMessage=((UserMessage) message);

                    for (Content content : userMessage.contents()) {
                        if(content instanceof TextContent){
                            logger.info("用户消息 [User][{}]: {}", userMessage.name(),((TextContent)content).text());
                        }else  if(content instanceof ImageContent){
                            logger.info("用户消息 [User][{}]: {}", userMessage.name(),"Image");
                        }else  if(content instanceof VideoContent){
                            logger.info("用户消息 [User][{}]: {}", userMessage.name(),"Video");
                        }else  if(content instanceof AudioContent){
                            logger.info("用户消息 [User][{}]: {}", userMessage.name(),"Audio");
                        }else  if(content instanceof PdfFileContent){
                            logger.info("用户消息 [User][{}]: {}", userMessage.name(),"PdfFile");
                        }else{
                            logger.info("用户消息 [User][{}]: {}", userMessage.name(),"未知");
                        }
                    }
                } else if (message instanceof AiMessage) {
                    AiMessage aiMessage = (AiMessage) message;
                    if(aiMessage.thinking()!=null){
                        logger.info("助手消息 [Ai thinking]: {}", aiMessage.thinking());
                    } if(aiMessage.text()!=null){
                        logger.info("助手消息 [Ai text]: {}", aiMessage.text());
                    }
                    if(aiMessage.toolExecutionRequests()!=null && !aiMessage.toolExecutionRequests().isEmpty()){
                        logger.info("助手消息 [Ai toolExecutionRequests]: {}", JSON.toJSONString(aiMessage.toolExecutionRequests().stream().map(x->x.id()+" "+x.name()+" "+x.arguments()).collect(Collectors.toList())));
                    }
                } else if (message instanceof SystemMessage) {
                    logger.info("系统消息 [System]: {}", ((SystemMessage) message).text());
                } else if (message instanceof ToolExecutionResultMessage) {
                    ToolExecutionResultMessage toolExecutionResultMessage = (ToolExecutionResultMessage) message;
                    logger.info("工具执行结果 [Tool][{}][{}]: {}",toolExecutionResultMessage.toolName(),toolExecutionResultMessage.id(), toolExecutionResultMessage.text());
                } else {
                    logger.info("其他消息 [{}]: {}", message.getClass().getSimpleName(), message);
                }
            }
        }
        logger.info("==========================================");
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        ChatResponse response = responseContext.chatResponse();
        ChatResponseMetadata metadata = response.metadata();
        logger.info("========== LangChain4j 响应完成 ==========");
        logger.info("模型: {}", metadata.modelName());

        if (response.aiMessage() != null) {
            logger.info("助手回复:thinking {}", response.aiMessage().thinking());
            logger.info("助手回复:text {}", response.aiMessage().text());
            logger.info("助手回复:toolExecutionRequests {}", JSON.toJSONString(response.aiMessage().toolExecutionRequests()));
        }

        dev.langchain4j.model.output.TokenUsage tokenUsage = metadata.tokenUsage();
        if (tokenUsage != null) {
            logger.info("Token 用量:");
            logger.info("  - 输入 tokens: {}", tokenUsage.inputTokenCount());
            logger.info("  - 输出 tokens: {}", tokenUsage.outputTokenCount());
            logger.info("  - 总 tokens: {}", tokenUsage.totalTokenCount());

            if(tokenUsageService!=null){
                try {
                    com.agent.hopaw.model.TokenUsage record = new com.agent.hopaw.model.TokenUsage();
                    record.setAgentId(agentId);
                    record.setUserId(userId);
                    record.setSource(source.getValue());
                    record.setModelName(metadata.modelName());
                    record.setInputTokens(tokenUsage.inputTokenCount());
                    record.setOutputTokens(tokenUsage.outputTokenCount());
                    record.setTotalTokens(tokenUsage.totalTokenCount());
                    record.setCreateTime(LocalDateTime.now());
                    tokenUsageService.save(record);
                } catch (Exception e) {
                    logger.error("保存 Token 用量记录失败", e);
                }
            }
        }
        logger.info("==========================================");
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        logger.error("========== LangChain4j 请求错误 ==========");
        logger.error("错误: "+ errorContext.error().getMessage(),errorContext.error());
        logger.error("==========================================");
    }
}
