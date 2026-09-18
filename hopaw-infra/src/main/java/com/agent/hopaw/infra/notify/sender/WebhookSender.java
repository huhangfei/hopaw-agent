package com.agent.hopaw.infra.notify.sender;

import com.agent.hopaw.infra.constant.NotifyChannelTypeEnum;
import com.agent.hopaw.infra.model.entity.NotifyChannel;
import com.agent.hopaw.infra.notify.NotifySender;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.stereotype.Component;

/**
 * 通用 Webhook 发送器：配置 url（必填）+ headers（可选，自定义请求头）。
 * 以 POST JSON 发送 {"title":..., "content":...}，适配各类第三方接收端。
 */
@Component
public class WebhookSender extends AbstractHttpNotifySender implements NotifySender {

    @Override
    public String getType() {
        return NotifyChannelTypeEnum.WEBHOOK.getCode();
    }

    @Override
    public String send(NotifyChannel channel, String title, String content) {
        JSONObject cfg = parseConfig(channel.getConfig());
        String url = cfg.getString("url");
        if (url == null || url.isBlank()) {
            return "Webhook 渠道未配置 url";
        }
        JSONObject body = new JSONObject();
        body.put("title", title != null ? title : "");
        body.put("content", content != null ? content : "");
        body.put("channel", channel.getName());
        java.util.Map<String, String> headers = null;
        JSONObject headerObj = cfg.getJSONObject("headers");
        if (headerObj != null && !headerObj.isEmpty()) {
            headers = new java.util.LinkedHashMap<>();
            for (String key : headerObj.keySet()) {
                headers.put(key, headerObj.getString(key));
            }
        }
        return postJson(url, body.toJSONString(), headers);
    }
}
