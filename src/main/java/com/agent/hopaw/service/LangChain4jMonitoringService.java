package com.agent.hopaw.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
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
import org.springframework.stereotype.Service;

@Service
public class LangChain4jMonitoringService implements ChatModelListener {

    private static final Logger logger = LoggerFactory.getLogger(LangChain4jMonitoringService.class);

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        ChatRequest chatRequest = requestContext.chatRequest();
        logger.info("========== LangChain4j 请求开始 ==========");
        logger.info("模型: {}", chatRequest.parameters().modelName());

        if (chatRequest.messages() != null) {
            for (ChatMessage message : chatRequest.messages()) {
                if (message instanceof UserMessage) {
                    UserMessage userMessage=((UserMessage) message);

                    logger.info("用户消息 [User][{}]: {}", userMessage.name(),userMessage.singleText());
                } else if (message instanceof AiMessage) {
                    AiMessage aiMessage = (AiMessage) message;
                    logger.info("助手消息 [Ai thinking]: {}", aiMessage.thinking());
                    logger.info("助手消息 [Ai text]: {}", aiMessage.text());
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
            logger.info("助手回复: {}", response.aiMessage().text());
        }

        TokenUsage tokenUsage = metadata.tokenUsage();
        if (tokenUsage != null) {
            logger.info("Token 用量:");
            logger.info("  - 输入 tokens: {}", tokenUsage.inputTokenCount());
            logger.info("  - 输出 tokens: {}", tokenUsage.outputTokenCount());
            logger.info("  - 总 tokens: {}", tokenUsage.totalTokenCount());
        }
        logger.info("==========================================");
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        logger.error("========== LangChain4j 请求错误 ==========");
        logger.error("错误: {}", errorContext.error().getMessage());
        logger.error("==========================================");
    }
}
