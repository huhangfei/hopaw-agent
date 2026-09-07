package com.agent.hopaw.infra.util;

import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.service.tool.ToolExecution;

import java.util.List;

/**
 * 多模态消息工具
 * <p>
 * langchain4j 1.19.0 的 {@link ToolExecutionResultMessage#text()} 与 {@link ToolExecution#result()}
 * 仅在内容为单个文本时可用，contents 含多个元素或非文本内容（如图片）时会抛出 IllegalStateException。
 * 工具返回多模态内容（例如读取图片工具返回 文本摘要+图片）时，所有调用方必须通过本工具类安全提取文本。
 */
public final class MultimodalMessageUtils {

    private MultimodalMessageUtils() {
    }

    /**
     * 安全提取工具执行结果消息的文本
     *
     * @param message 工具执行结果消息，可为 null
     * @return 单文本内容返回其文本；多内容时拼接所有文本并以占位符标记非文本内容；无内容返回 null
     */
    public static String toolResultText(ToolExecutionResultMessage message) {
        if (message == null) {
            return null;
        }
        return contentsText(message.contents());
    }

    /**
     * 安全提取工具执行结果的文本
     *
     * @param toolExecution 工具执行结果，可为 null
     * @return 同 {@link #toolResultText(ToolExecutionResultMessage)} 语义
     */
    public static String toolResultText(ToolExecution toolExecution) {
        if (toolExecution == null) {
            return null;
        }
        return contentsText(toolExecution.resultContents());
    }

    /**
     * 安全提取消息内容列表的文本：单文本直接返回；多内容拼接文本并以占位符标记非文本内容
     */
    private static String contentsText(List<Content> contents) {
        if (contents == null || contents.isEmpty()) {
            return null;
        }
        if (contents.size() == 1 && contents.get(0) instanceof TextContent) {
            return ((TextContent) contents.get(0)).text();
        }
        StringBuilder sb = new StringBuilder();
        for (Content content : contents) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            if (content instanceof TextContent) {
                sb.append(((TextContent) content).text());
            } else {
                sb.append("[非文本内容: ").append(content.getClass().getSimpleName()).append("]");
            }
        }
        return sb.toString();
    }
}
