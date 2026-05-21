package com.agent.hopaw.tool.dingtalk;

import com.agent.hopaw.infra.model.dto.ToolConfigItem;
import com.agent.hopaw.infra.model.dto.ValidationRule;
import com.agent.hopaw.infra.service.ISysConfigService;
import com.agent.hopaw.infra.tool.AgentTool;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Properties;

public class DingTalkTool implements AgentTool {

    private static final Logger logger = LoggerFactory.getLogger(DingTalkTool.class);
    private static final String CONFIG_FILE = "dingtalk.properties";
    private static final String PROP_WEBHOOK_URL = "dingtalk.webhook.url";
    private static final String PROP_SECRET = "dingtalk.secret";
    private static final String ENV_WEBHOOK_URL = "DINGTALK_WEBHOOK_URL";
    private static final String ENV_SECRET = "DINGTALK_SECRET";
    private static final String CONFIG_KEY_WEBHOOK = "webhookUrl";
    private static final String CONFIG_KEY_SECRET = "secret";

    private final HttpClient httpClient;
    private volatile String defaultWebhookUrl;
    private volatile String defaultSecret;
    @Autowired
    private ISysConfigService sysConfigService;

    public DingTalkTool() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String getName() {
        return "dingtalkNotify";
    }

    @Override
    public String getDescription() {
        return "钉钉机器人通知工具，支持发送文本和Markdown消息到钉钉群";
    }

    @Override
    public String getIcon() {
        return "dingtalk-notify.svg";
    }

    @Override
    public String getKeyword() {
        return "钉钉,通知,消息,dingtalk,群消息,机器人";
    }

    @Override
    public List<ToolConfigItem> getConfigItems() {
        return List.of(
                new ToolConfigItem(CONFIG_KEY_WEBHOOK, "Webhook地址", "钉钉群机器人的完整Webhook地址（https://oapi.dingtalk.com/robot/send?access_token=xxx）",
                        ToolConfigItem.ConfigType.TEXT_PASSWORD)
                        .validation(new ValidationRule().required()),
                new ToolConfigItem(CONFIG_KEY_SECRET, "加签密钥", "机器人安全设置页面，加签一栏显示的SEC开头的密钥字符串（可选，不填则不使用加签）",
                        ToolConfigItem.ConfigType.TEXT_PASSWORD)
        );
    }

    @Override
    public void asyncInit() {
        String prefix = getConfigPrefix();
        sysConfigService.setSensitiveKeys(prefix + CONFIG_KEY_WEBHOOK, prefix + CONFIG_KEY_SECRET);
        loadConfig();
    }

    @Override
    public void onConfigChanged() {
        loadConfig();
    }

    private void loadConfig() {
        String prefix = getConfigPrefix();

        if (sysConfigService != null) {
            String dbWebhook = sysConfigService.getValueByKey(prefix + CONFIG_KEY_WEBHOOK, null);
            if (dbWebhook != null && !dbWebhook.isBlank()) {
                defaultWebhookUrl = dbWebhook.trim();
                logger.info("Loaded DingTalk webhook from database config");
            }
            String dbSecret = sysConfigService.getValueByKey(prefix + CONFIG_KEY_SECRET, null);
            if (dbSecret != null && !dbSecret.isBlank()) {
                defaultSecret = dbSecret.trim();
                logger.info("Loaded DingTalk secret from database config");
            }
        }

        if (defaultWebhookUrl == null || defaultWebhookUrl.isBlank()) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
                if (in != null) {
                    Properties props = new Properties();
                    props.load(in);
                    defaultWebhookUrl = props.getProperty(PROP_WEBHOOK_URL);
                    defaultSecret = props.getProperty(PROP_SECRET);
                    if (defaultWebhookUrl != null && !defaultWebhookUrl.isBlank()) {
                        logger.info("Loaded DingTalk webhook/secret from {}", CONFIG_FILE);
                    }
                }
            } catch (IOException e) {
                logger.debug("No {} found on classpath", CONFIG_FILE);
            }
        }

        if (defaultWebhookUrl == null || defaultWebhookUrl.isBlank()) {
            defaultWebhookUrl = System.getProperty(PROP_WEBHOOK_URL);
            if (defaultWebhookUrl == null || defaultWebhookUrl.isBlank()) {
                defaultWebhookUrl = System.getenv(ENV_WEBHOOK_URL);
            }
        }
        if (defaultSecret == null || defaultSecret.isBlank()) {
            defaultSecret = System.getProperty(PROP_SECRET);
            if (defaultSecret == null || defaultSecret.isBlank()) {
                defaultSecret = System.getenv(ENV_SECRET);
            }
        }
    }

    @Tool("发送纯文本消息到钉钉群")
    public String sendTextToDingTalk(
            @P("要发送的文本内容") String message,
            @P(value = "钉钉机器人webhook地址，不传则使用已配置的地址", required = false) String webhookUrl) {
        String url = resolveWebhookUrl(webhookUrl);
        if (url == null) {
            return "错误: 未配置钉钉机器人webhook地址。请到 工具配置 页面设置，或传入 webhookUrl 参数";
        }

        String json = buildTextPayload(message);
        return doPost(url, json, "文本消息");
    }

    @Tool("发送Markdown格式消息到钉钉群")
    public String sendMarkdownToDingTalk(
            @P("消息标题") String title,
            @P("Markdown格式的消息内容") String text,
            @P(value = "钉钉机器人webhook地址，不传则使用已配置的地址", required = false) String webhookUrl) {
        String url = resolveWebhookUrl(webhookUrl);
        if (url == null) {
            return "错误: 未配置钉钉机器人webhook地址。请到 工具配置 页面设置，或传入 webhookUrl 参数";
        }

        String json = buildMarkdownPayload(title, text);
        return doPost(url, json, "Markdown消息");
    }

    private String resolveWebhookUrl(String paramUrl) {
        String baseUrl = (paramUrl != null && !paramUrl.isBlank()) ? paramUrl.trim() : defaultWebhookUrl;
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        String secret = (paramUrl != null && !paramUrl.isBlank()) ? null : defaultSecret;
        if (secret != null && !secret.isBlank()) {
            baseUrl = appendSign(baseUrl, secret);
        }
        return baseUrl;
    }

    private String appendSign(String webhookUrl, String secret) {
        try {
            long timestamp = System.currentTimeMillis();
            String stringToSign = timestamp + "\n" + secret;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            String sign = URLEncoder.encode(Base64.getEncoder().encodeToString(signData), StandardCharsets.UTF_8.toString());
            return webhookUrl + "&timestamp=" + timestamp + "&sign=" + sign;
        } catch (Exception e) {
            logger.error("Failed to compute DingTalk sign", e);
            return webhookUrl;
        }
    }

    private String doPost(String webhookUrl, String jsonBody, String msgType) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            String body = response.body();

            if (status == 200 && body.contains("\"errcode\":0")) {
                logger.info("DingTalk {} sent successfully", msgType);
                return "钉钉" + msgType + "发送成功";
            }

            logger.warn("DingTalk {} send failed: status={}, body={}", msgType, status, body);
            return "钉钉" + msgType + "发送失败: HTTP " + status + ", " + extractErrmsg(body);
        } catch (Exception e) {
            logger.error("DingTalk {} send error", msgType, e);
            return "钉钉" + msgType + "发送异常: " + e.getMessage();
        }
    }

    private static String buildTextPayload(String message) {
        return "{\"msgtype\":\"text\",\"text\":{\"content\":" + jsonEscape(message) + "}}";
    }

    private static String buildMarkdownPayload(String title, String text) {
        return "{\"msgtype\":\"markdown\",\"markdown\":{\"title\":" + jsonEscape(title)
                + ",\"text\":" + jsonEscape(text) + "}}";
    }

    private static String jsonEscape(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:   sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }

    private static String extractErrmsg(String body) {
        if (body == null) return "";
        int idx = body.indexOf("\"errmsg\":\"");
        if (idx < 0) return body;
        int start = idx + 10;
        int end = body.indexOf('"', start);
        return end > start ? body.substring(start, end) : body;
    }
}