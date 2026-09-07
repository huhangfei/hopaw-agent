package com.agent.hopaw.infra.memory;

import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.VideoContent;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;

import java.util.ArrayList;
import java.util.List;

/**
 * 支持多模态内容的 Token 估算器
 * <p>
 * langchain4j 的 {@link OpenAiTokenCountEstimator} 仅处理文本内容，估算含
 * {@link ImageContent} 的消息时会抛出 "Unknown content type" 异常，导致带图会话直接失败。
 * 本估算器在其基础上补充多模态内容的估算，仅用于窗口记忆的容量淘汰，追求量级正确而非精确：
 * <ul>
 *   <li>ImageContent：按 OpenAI 视觉计费规则量级估算（LOW 固定 85；HIGH/AUTO 按典型高精度图片约 1100）</li>
 *   <li>AudioContent / PdfFileContent / VideoContent：按固定值估算</li>
 * </ul>
 * 文本部分与消息结构开销仍复用 {@link OpenAiTokenCountEstimator}，保持与纯文本场景一致的估算口径。
 */
public class MultimodalTokenCountEstimator implements TokenCountEstimator {

    /** OpenAI 视觉计费：低精度图片固定 85 token */
    private static final int IMAGE_LOW_DETAIL_TOKENS = 85;
    /** 高精度/自动精度图片按典型高精度图片量级估算 */
    private static final int IMAGE_HIGH_DETAIL_TOKENS = 1100;
    /** 音频内容固定估算值 */
    private static final int AUDIO_CONTENT_TOKENS = 500;
    /** PDF 内容固定估算值 */
    private static final int PDF_CONTENT_TOKENS = 1000;
    /** 视频内容固定估算值 */
    private static final int VIDEO_CONTENT_TOKENS = 1000;
    /** 无法识别的内容类型兜底估算值 */
    private static final int UNKNOWN_CONTENT_TOKENS = 100;
    /** 纯多模态消息（无文本部分）时的消息结构开销近似值 */
    private static final int MESSAGE_OVERHEAD_TOKENS = 4;

    private final OpenAiTokenCountEstimator delegate;

    public MultimodalTokenCountEstimator(String modelName) {
        this.delegate = new OpenAiTokenCountEstimator(modelName);
    }

    @Override
    public int estimateTokenCountInText(String text) {
        return delegate.estimateTokenCountInText(text);
    }

    @Override
    public int estimateTokenCountInMessage(ChatMessage message) {
        if (message instanceof ToolExecutionResultMessage) {
            // 1.19.0 的 OpenAiTokenCountEstimator 对工具结果调用 text()，多模态内容时抛 IllegalStateException
            return estimateToolResultTokens((ToolExecutionResultMessage) message);
        }
        if (!(message instanceof UserMessage)) {
            return delegate.estimateTokenCountInMessage(message);
        }
        UserMessage userMessage = (UserMessage) message;
        int total = 0;
        List<Content> textContents = new ArrayList<>();
        for (Content content : userMessage.contents()) {
            if (content instanceof TextContent) {
                textContents.add(content);
            } else if (content instanceof ImageContent) {
                total += estimateImageTokens((ImageContent) content);
            } else if (content instanceof AudioContent) {
                total += AUDIO_CONTENT_TOKENS;
            } else if (content instanceof PdfFileContent) {
                total += PDF_CONTENT_TOKENS;
            } else if (content instanceof VideoContent) {
                total += VIDEO_CONTENT_TOKENS;
            } else if (content != null) {
                total += UNKNOWN_CONTENT_TOKENS;
            }
        }
        if (!textContents.isEmpty()) {
            // 文本部分交给委托估算器，保持消息开销与编码口径一致
            UserMessage textOnlyMessage = userMessage.name() != null
                    ? UserMessage.from(userMessage.name(), textContents)
                    : UserMessage.from(textContents);
            total += delegate.estimateTokenCountInMessage(textOnlyMessage);
        } else {
            total += MESSAGE_OVERHEAD_TOKENS;
        }
        return total;
    }

    /**
     * 估算多模态工具结果消息的 Token：文本部分按文本估算，非文本内容按类型估算
     */
    private int estimateToolResultTokens(ToolExecutionResultMessage message) {
        int total = 0;
        boolean hasText = false;
        for (Content content : message.contents()) {
            if (content instanceof TextContent) {
                hasText = true;
                total += delegate.estimateTokenCountInText(((TextContent) content).text());
            } else if (content instanceof ImageContent) {
                total += estimateImageTokens((ImageContent) content);
            } else if (content instanceof AudioContent) {
                total += AUDIO_CONTENT_TOKENS;
            } else if (content instanceof PdfFileContent) {
                total += PDF_CONTENT_TOKENS;
            } else if (content instanceof VideoContent) {
                total += VIDEO_CONTENT_TOKENS;
            } else if (content != null) {
                total += UNKNOWN_CONTENT_TOKENS;
            }
        }
        return hasText ? total + MESSAGE_OVERHEAD_TOKENS : total;
    }

    @Override
    public int estimateTokenCountInMessages(Iterable<ChatMessage> messages) {
        int total = 0;
        for (ChatMessage message : messages) {
            total += estimateTokenCountInMessage(message);
        }
        return total;
    }

    private int estimateImageTokens(ImageContent content) {
        ImageContent.DetailLevel level = content.detailLevel();
        if (level == null || level == ImageContent.DetailLevel.AUTO || level == ImageContent.DetailLevel.HIGH) {
            return IMAGE_HIGH_DETAIL_TOKENS;
        }
        return IMAGE_LOW_DETAIL_TOKENS;
    }
}
