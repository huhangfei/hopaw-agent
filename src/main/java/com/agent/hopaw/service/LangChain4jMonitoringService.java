package com.agent.hopaw.service;

import com.alibaba.fastjson2.JSON;
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
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

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
        logger.error("错误: "+ errorContext.error().getMessage(),errorContext.error());
        logger.error("==========================================");
    }
}
