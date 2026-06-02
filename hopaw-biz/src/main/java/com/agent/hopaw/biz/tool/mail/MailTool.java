package com.agent.hopaw.biz.tool.mail;

import com.agent.hopaw.infra.tool.ToolSecurityLevel;
import com.agent.hopaw.biz.util.MailUtil;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.agent.hopaw.infra.tool.AgentTool;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Component("mailTool")
public class MailTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(MailTool.class);

    private final MailUtil mailUtil;

    public MailTool(MailUtil mailUtil) {
        this.mailUtil = mailUtil;
    }

    @Override
    public String getName() {
        return "mailTool";
    }

    @Override
    public String getDescription() {
        return "发送邮件，支持纯文本和HTML格式";
    }

    @Override
    public String getIcon() {
        return "mail-tool.svg";
    }

    @Override
    public String getKeyword() {
        return "邮件";
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.ALL_REQUIRE_APPROVAL)
    @Tool("发送纯文本邮件。")
    public String sendTextMail(
            @P(description = "收件人邮箱地址") String to,
            @P(description = "邮件主题") String subject,
            @P(description = "邮件正文（纯文本格式）") String text) {
        try {
            mailUtil.sendSimpleMail(to, subject, text);
            log.info("工具调用: 发送文本邮件至 {}", to);
            return "邮件已成功发送至 " + to;
        } catch (Exception e) {
            log.error("工具调用: 发送邮件失败", e);
            return "邮件发送失败: " + e.getMessage();
        }
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.ALL_REQUIRE_APPROVAL)
    @Tool("发送HTML格式邮件。")
    public String sendHtmlMail(
            @P(description = "收件人邮箱地址") String to,
            @P(description = "邮件主题") String subject,
            @P(description = "邮件正文（HTML格式）") String html) {
        try {
            mailUtil.sendHtmlMail(to, subject, html);
            log.info("工具调用: 发送HTML邮件至 {}", to);
            return "邮件已成功发送至 " + to;
        } catch (Exception e) {
            log.error("工具调用: 发送HTML邮件失败", e);
            return "邮件发送失败: " + e.getMessage();
        }
    }
}
