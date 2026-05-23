package com.agent.hopaw.pluginrepo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.util.Properties;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    @Value("${mail.host:}")
    private String mailHost;

    @Value("${mail.port:587}")
    private int mailPort;

    @Value("${mail.username:}")
    private String mailUsername;

    @Value("${mail.password:}")
    private String mailPassword;

    @Value("${mail.from:}")
    private String mailFrom;

    public boolean isConfigured() {
        return mailHost != null && !mailHost.isBlank() 
                && mailUsername != null && !mailUsername.isBlank()
                && mailPassword != null && !mailPassword.isBlank();
    }

    private JavaMailSenderImpl buildSender() {
        if (!isConfigured()) {
            throw new IllegalStateException("邮件服务未配置");
        }

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(mailHost);
        sender.setPort(mailPort);
        sender.setUsername(mailUsername);
        sender.setPassword(mailPassword);

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");

        return sender;
    }

    public void sendSimpleMail(String to, String subject, String text) {
        try {
            JavaMailSenderImpl sender = buildSender();
            SimpleMailMessage message = new SimpleMailMessage();
            if (mailFrom != null && !mailFrom.isBlank()) {
                message.setFrom(mailFrom);
            }
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            sender.send(message);
            log.info("邮件已发送至: {}", to);
        } catch (Exception e) {
            log.error("发送邮件失败", e);
            throw new RuntimeException("发送邮件失败: " + e.getMessage());
        }
    }
}
