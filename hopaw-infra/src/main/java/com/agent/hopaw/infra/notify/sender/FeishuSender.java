package com.agent.hopaw.infra.notify.sender;

import com.agent.hopaw.infra.constant.NotifyChannelTypeEnum;
import com.agent.hopaw.infra.model.entity.NotifyChannel;
import com.agent.hopaw.infra.notify.NotifySender;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 飞书群机器人发送器：配置 webhookUrl（必填）+ secret（可选签名校验）。
 */
@Component
public class FeishuSender extends AbstractHttpNotifySender implements NotifySender {

    @Override
    public String getType() {
        return NotifyChannelTypeEnum.FEISHU.getCode();
    }

    @Override
    public String send(NotifyChannel channel, String title, String content) {
        JSONObject cfg = parseConfig(channel.getConfig());
        String webhookUrl = cfg.getString("webhookUrl");
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return "飞书渠道未配置 webhookUrl";
        }
        String secret = cfg.getString("secret");
        String url = webhookUrl;
        try {
            if (secret != null && !secret.isBlank()) {
                url = signUrl(webhookUrl, secret);
            }
            // 飞书文本消息：标题与内容拼接
            String text = (title != null && !title.isBlank() ? title + "\n" : "") + (content != null ? content : "");
            JSONObject body = new JSONObject();
            body.put("msg_type", "text");
            JSONObject contentObj = new JSONObject();
            contentObj.put("text", text);
            body.put("content", contentObj);
            return postJson(url, body.toJSONString(), null);
        } catch (Exception e) {
            return "飞书通知发送失败: " + e.getMessage();
        }
    }

    /**
     * 飞书签名：以 timestamp + "\n" + secret 作为 HmacSHA256 的 key 与数据，Base64 后 URLEncode。
     */
    private String signUrl(String webhookUrl, String secret) throws Exception {
        long timestamp = System.currentTimeMillis();
        String stringToSign = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(stringToSign.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        String sign = URLEncoder.encode(Base64.getEncoder().encodeToString(signData), StandardCharsets.UTF_8);
        return webhookUrl + (webhookUrl.contains("?") ? "&" : "?") + "timestamp=" + timestamp + "&sign=" + sign;
    }
}
