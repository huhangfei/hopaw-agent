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
 * 钉钉群机器人发送器：配置 webhookUrl（必填）+ secret（可选加签）。
 */
@Component
public class DingTalkSender extends AbstractHttpNotifySender implements NotifySender {

    @Override
    public String getType() {
        return NotifyChannelTypeEnum.DINGTALK.getCode();
    }

    @Override
    public String send(NotifyChannel channel, String title, String content) {
        JSONObject cfg = parseConfig(channel.getConfig());
        String webhookUrl = cfg.getString("webhookUrl");
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return "钉钉渠道未配置 webhookUrl";
        }
        String secret = cfg.getString("secret");
        String url = webhookUrl;
        try {
            if (secret != null && !secret.isBlank()) {
                url = signUrl(webhookUrl, secret);
            }
            // 钉钉文本消息：标题与内容拼接为一行文本（钉钉 text 不支持独立标题字段）
            String text = (title != null && !title.isBlank() ? title + "\n" : "") + (content != null ? content : "");
            JSONObject body = new JSONObject();
            body.put("msgtype", "text");
            JSONObject textObj = new JSONObject();
            textObj.put("content", text);
            body.put("text", textObj);
            String err = postJson(url, body.toJSONString(), null);
            return err;
        } catch (Exception e) {
            return "钉钉通知发送失败: " + e.getMessage();
        }
    }

    /**
     * 加签：timestamp + "\n" + secret 做 HmacSHA256 后 Base64 + URLEncode，附加到 webhook 地址。
     */
    private String signUrl(String webhookUrl, String secret) throws Exception {
        long timestamp = System.currentTimeMillis();
        String stringToSign = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        String sign = URLEncoder.encode(Base64.getEncoder().encodeToString(signData), StandardCharsets.UTF_8);
        return webhookUrl + (webhookUrl.contains("?") ? "&" : "?") + "timestamp=" + timestamp + "&sign=" + sign;
    }
}
