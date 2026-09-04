package com.agent.hopaw.infra.executor;

import com.agent.hopaw.infra.constant.AgentExecutorBizTypeEnum;
import com.agent.hopaw.infra.event.AgentMessageEvent;
import com.agent.hopaw.infra.model.dto.AiMessageBaseInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

//@Component
public class WsStressTestBean {

    private static final Logger log = LoggerFactory.getLogger(WsStressTestBean.class);

    private static final String MARKDOWN_CONTENT =
            "# 性能压测消息\n\n" +
            "这是一段包含 **Markdown** 格式的压测内容，用于测试前端 WebSocket 流式接收与渲染性能。\n\n" +
            "## 列表示例\n\n" +
            "- 第一项：Java 17 + Spring Boot 2.7.18\n" +
            "- 第二项：LangChain4j 1.19.0 AI Agent 平台\n" +
            "- 第三项：SQLite + MyBatis + WebSocket + Thymeleaf\n" +
            "- 第四项：Playwright / JSch / Jsoup 工具集成\n\n" +
            "## 代码块示例\n\n" +
            "```java\n" +
            "public static void main(String[] args) {\n" +
            "    System.out.println(\"Hello, HoPaw Agent!\");\n" +
            "}\n" +
            "```\n\n" +
            "## 表格示例\n\n" +
            "| 模块 | 说明 |\n" +
            "|------|------|\n" +
            "| hopaw-contract | 接口、DTO、枚举 |\n" +
            "| hopaw-infra | 数据访问、AI 模型、插件加载 |\n" +
            "| hopaw-biz | Agent 工具、业务逻辑 |\n" +
            "| hopaw-app | Web 层、WebSocket、UI |\n\n" +
            "## 长文本填充\n\n" +
            "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. " +
            "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. " +
            "Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur.\n\n" +
            "中文测试：这是一段较长的中文文本，用于模拟真实 AI 回复场景下的流式传输效果。" +
            "WebSocket 是 HTML5 开始提供的一种在单个 TCP 连接上进行全双工通讯的协议。" +
            "它使得客户端和服务器之间的数据交换变得更加简单高效，允许服务端主动向客户端推送数据。\n\n" +
            "> 引用块：性能测试的核心指标包括消息吞吐量、端到端延迟、前端渲染帧率以及内存占用。\n\n" +
            "---\n\n" +
            "`行内代码` 测试 | [链接示例](https://example.com) | ~~删除线~~\n\n" +
            "**压测结束标记**";

    private final ApplicationEventPublisher eventPublisher;
    private int seq = 0;
    private int charPos = 0;
    private String currentRequestId = null;

    public WsStressTestBean(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
        log.info("WebSocket 压测已启用，每 100ms 流式发送 1-5 个字符");
    }

    //@Scheduled(fixedRate = 2)
    public void sendChunk() {
        if (charPos == 0) {
            currentRequestId = "stress-" + UUID.randomUUID().toString().substring(0, 8);
            seq++;
        }

        int remaining = MARKDOWN_CONTENT.length() - charPos;
        if (remaining <= 0) {
            finishCurrentRound();
            return;
        }

        int chunkSize = ThreadLocalRandom.current().nextInt(1, 6);
        chunkSize = Math.min(chunkSize, remaining);
        String chunk = MARKDOWN_CONTENT.substring(charPos, charPos + chunkSize);
        charPos += chunkSize;

        AiMessageBaseInfo message = AiMessageBaseInfo.chunk("990d8c6eabd6491fa32b4e7638b49359", currentRequestId, chunk);
        message.setBizType(AgentExecutorBizTypeEnum.WorkflowTaskChat);
        eventPublisher.publishEvent(new AgentMessageEvent("1", 1L, message));

        if (charPos >= MARKDOWN_CONTENT.length()) {
            finishCurrentRound();
        }
    }

    private void finishCurrentRound() {
        sendDone();
        charPos = 0;
        if (seq % 10 == 0) {
            log.info("压测第 {} 轮完成", seq);
        }
    }

    private void sendDone() {
        AiMessageBaseInfo done = AiMessageBaseInfo.done(null, currentRequestId);
        done.setBizType(AgentExecutorBizTypeEnum.WorkflowTaskChat);
        eventPublisher.publishEvent(new AgentMessageEvent(null, 0L, done));
    }
}
