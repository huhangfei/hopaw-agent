package com.agent.hopaw.util;

import com.agent.hopaw.model.SysConfig;
import com.agent.hopaw.service.SysConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

@Component
public class MailUtil {

    private static final Logger log = LoggerFactory.getLogger(MailUtil.class);

    private static final String KEY_HOST = "mail_host";
    private static final String KEY_PORT = "mail_port";
    private static final String KEY_USERNAME = "mail_username";
    private static final String KEY_PASSWORD = "mail_password";
    private static final String KEY_FROM = "mail_from";

    private final SysConfigService sysConfigService;

    public MailUtil(SysConfigService sysConfigService) {
        this.sysConfigService = sysConfigService;
    }

    /**
     * 从 SysConfig 读取邮件配置，构建 JavaMailSenderImpl
     */
    private JavaMailSenderImpl buildSender(Map<String, String> cfg) {
        String host = cfg.get(KEY_HOST);
        String portStr = cfg.get(KEY_PORT);
        String username = cfg.get(KEY_USERNAME);
        String password = cfg.get(KEY_PASSWORD);

        if (host == null || host.isBlank()) {
            throw new IllegalStateException("邮件服务器未配置，请在设置页面填写邮件配置");
        }

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(portStr != null && !portStr.isBlank() ? Integer.parseInt(portStr) : 587);
        sender.setUsername(username);
        sender.setPassword(password);

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");

        return sender;
    }

    /**
     * 读取所有配置并转为 Map
     */
    private Map<String, String> loadConfig() {
        List<SysConfig> configs = sysConfigService.getAll();
        return configs.stream()
                .collect(Collectors.toMap(SysConfig::getConfigKey, SysConfig::getConfigValue, (a, b) -> a));
    }

    /**
     * 发送简单文本邮件
     */
    public void sendSimpleMail(String to, String subject, String text) {
        Map<String, String> cfg = loadConfig();
        JavaMailSenderImpl sender = buildSender(cfg);
        SimpleMailMessage message = new SimpleMailMessage();
        String from = cfg.get(KEY_FROM);
        if (from != null && !from.isBlank()) message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        sender.send(message);
        log.info("简单邮件已发送至: {}", to);
    }

    /**
     * 发送 HTML 邮件
     */
    public void sendHtmlMail(String to, String subject, String html) {
        Map<String, String> cfg = loadConfig();
        JavaMailSenderImpl sender = buildSender(cfg);
        MimeMessage mimeMessage = sender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            String from = cfg.get(KEY_FROM);
            if (from != null && !from.isBlank()) helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            sender.send(mimeMessage);
            log.info("HTML邮件已发送至: {}", to);
        } catch (MessagingException e) {
            throw new RuntimeException("发送HTML邮件失败", e);
        }
    }

    /**
     * 发送带附件的邮件
     */
    public void sendAttachmentMail(String to, String subject, String text, File... attachments) {
        Map<String, String> cfg = loadConfig();
        JavaMailSenderImpl sender = buildSender(cfg);
        MimeMessage mimeMessage = sender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            String from = cfg.get(KEY_FROM);
            if (from != null && !from.isBlank()) helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, true);
            for (File attachment : attachments) {
                if (attachment != null && attachment.exists()) {
                    helper.addAttachment(attachment.getName(), attachment);
                }
            }
            sender.send(mimeMessage);
            log.info("附件邮件已发送至: {}, 附件数量: {}", to, attachments.length);
        } catch (MessagingException e) {
            throw new RuntimeException("发送附件邮件失败", e);
        }
    }

    /**
     * 测试邮件配置是否可用
     */
    public boolean testConnection() {
        try {
            JavaMailSenderImpl sender = buildSender(loadConfig());
            sender.testConnection();
            return true;
        } catch (Exception e) {
            log.warn("邮件服务器连接测试失败: {}", e.getMessage());
            return false;
        }
    }
}
