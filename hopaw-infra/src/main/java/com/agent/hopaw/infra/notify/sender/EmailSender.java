package com.agent.hopaw.infra.notify.sender;

import com.agent.hopaw.infra.constant.NotifyChannelTypeEnum;
import com.agent.hopaw.infra.model.entity.NotifyChannel;
import com.agent.hopaw.infra.model.entity.SysConfig;
import com.agent.hopaw.infra.notify.NotifySender;
import com.agent.hopaw.infra.service.ISysConfigService;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * 邮件发送器：渠道配置 receivers（逗号分隔的收件邮箱）；SMTP 服务器复用系统设置中的邮件配置
 * （mail_host / mail_port / mail_username / mail_password / mail_from）。
 */
@Component
public class EmailSender implements NotifySender {

    private static final String KEY_HOST = "mail_host";
    private static final String KEY_PORT = "mail_port";
    private static final String KEY_USERNAME = "mail_username";
    private static final String KEY_PASSWORD = "mail_password";
    private static final String KEY_FROM = "mail_from";

    private final ISysConfigService sysConfigService;

    public EmailSender(ISysConfigService sysConfigService) {
        this.sysConfigService = sysConfigService;
        this.sysConfigService.setSensitiveKeys(KEY_PASSWORD);
    }

    @Override
    public String getType() {
        return NotifyChannelTypeEnum.EMAIL.getCode();
    }

    @Override
    public String send(NotifyChannel channel, String title, String content) {
        JSONObject cfg;
        try {
            cfg = parseConfig(channel.getConfig());
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
        String receivers = cfg.getString("receivers");
        if (receivers == null || receivers.isBlank()) {
            return "邮件渠道未配置收件人 receivers";
        }
        try {
            JavaMailSenderImpl sender = buildSender();
            SimpleMailMessage message = new SimpleMailMessage();
            String from = getCfg().get(KEY_FROM);
            if (from != null && !from.isBlank()) {
                message.setFrom(from);
            }
            message.setTo(receivers.split("[,;\\s]+"));
            message.setSubject(title != null && !title.isBlank() ? title : "通知");
            message.setText(content != null ? content : "");
            sender.send(message);
            return null;
        } catch (Exception e) {
            return "邮件发送失败: " + e.getMessage();
        }
    }

    private JSONObject parseConfig(String config) {
        if (config == null || config.isBlank()) {
            return new JSONObject();
        }
        return com.alibaba.fastjson2.JSON.parseObject(config);
    }

    /** 读取系统设置中的 SMTP 配置 */
    private Map<String, String> getCfg() {
        List<SysConfig> configs = sysConfigService.getByKeys(List.of(KEY_HOST, KEY_PORT, KEY_USERNAME, KEY_PASSWORD, KEY_FROM));
        return configs.stream()
                .collect(Collectors.toMap(SysConfig::getConfigKey, SysConfig::getConfigValue, (a, b) -> a));
    }

    private JavaMailSenderImpl buildSender() {
        Map<String, String> cfg = getCfg();
        String host = cfg.get(KEY_HOST);
        if (host == null || host.isBlank()) {
            throw new IllegalStateException("邮件服务器未配置，请在设置页面填写邮件配置");
        }
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        String portStr = cfg.get(KEY_PORT);
        sender.setPort(portStr != null && !portStr.isBlank() ? Integer.parseInt(portStr) : 587);
        sender.setUsername(cfg.get(KEY_USERNAME));
        sender.setPassword(cfg.get(KEY_PASSWORD));
        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");
        return sender;
    }
}
