package com.agent.hopaw.service;


import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.observability.api.event.AiServiceRequestIssuedEvent;
import dev.langchain4j.observability.api.listener.AiServiceRequestIssuedListener;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * 自动清理由于服务中断而残留的“孤儿”工具调用的监听器。
 * 在每次向 LLM 发送请求前自动移除不完整的 AiMessage。
 */
public class OrphanToolCallCleanupListener implements AiServiceRequestIssuedListener {

    @Override
    public void onEvent(AiServiceRequestIssuedEvent context) {
        // 获取即将发送给 LLM 的消息列表
        java.util.List<ChatMessage> messages = context.request().messages();

        // 删除存在孤儿工具调用的 AiMessage（或者替换为错误消息）
        // removeOrphanRequests(messages);
        // 如果你想用错误消息替换，可以用：
        replaceOrphanRequestsWithError(messages);
    }

    /**
     * 方式一：直接移除包含未完成工具调用的 AiMessage
     */
    private void removeOrphanRequests(java.util.List<ChatMessage> messages) {
        // 先收集所有已完成的工具调用 ID
        Set<String> resolvedCallIds = new HashSet<>();
        for (ChatMessage msg : messages) {
            if (msg instanceof ToolExecutionResultMessage) {
                resolvedCallIds.add(((ToolExecutionResultMessage) msg).id());
            }
        }

        // 使用迭代器安全移除元素
        Iterator<ChatMessage> iterator = messages.iterator();
        while (iterator.hasNext()) {
            ChatMessage msg = iterator.next();
            if (msg instanceof AiMessage aiMsg && aiMsg.hasToolExecutionRequests()) {
                // 检查该 AiMessage 的所有工具调用请求是否都已得到回应
                boolean allResolved = aiMsg.toolExecutionRequests().stream()
                        .allMatch(req -> resolvedCallIds.contains(req.id()));
                if (!allResolved) {
                    // 存在孤儿请求，移除整个 AiMessage
                    iterator.remove();
                }
            }
        }
    }

    /**
     * 方式二：将包含孤儿调用的 AiMessage 替换为 ToolExecutionResultMessage 错误提示。
     * 这样 LLM 可以知道工具调用失败，并自行决定重试或向用户说明。
     */
    private void replaceOrphanRequestsWithError(java.util.List<ChatMessage> messages) {
        Set<String> resolvedCallIds = new HashSet<>();
        for (ChatMessage msg : messages) {
            if (msg instanceof ToolExecutionResultMessage) {
                resolvedCallIds.add(((ToolExecutionResultMessage) msg).id());
            }
        }

        // 用 ListIterator 来在遍历时替换元素
        java.util.ListIterator<ChatMessage> iterator = messages.listIterator();
        while (iterator.hasNext()) {
            ChatMessage msg = iterator.next();
            if (msg instanceof AiMessage aiMsg && aiMsg.hasToolExecutionRequests()) {
                boolean allResolved = aiMsg.toolExecutionRequests().stream()
                        .allMatch(req -> resolvedCallIds.contains(req.id()));
                if (!allResolved) {
                    // 为每个未解决的请求生成一个错误结果消息
                    for (ToolExecutionRequest req : aiMsg.toolExecutionRequests()) {
                        if (!resolvedCallIds.contains(req.id())) {
                            ToolExecutionResultMessage errorMsg = new ToolExecutionResultMessage(
                                    req.id(),
                                    req.name(),
                                    "工具调用因服务中断而失败，请重试或向用户说明情况。"
                            );
                            iterator.add(errorMsg); // 插入到 AiMessage 之后
                        }
                    }
                    // 移除原来的 AiMessage
                    iterator.remove();
                }
            }
        }
    }
}
