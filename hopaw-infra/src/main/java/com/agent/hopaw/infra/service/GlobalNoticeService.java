package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.event.GlobalNoticeEvent;
import com.agent.hopaw.infra.enums.GlobalNoticeTypeEnum;
import com.agent.hopaw.infra.model.dto.GlobalNoticeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 全局消息推送服务实现：发布 GlobalNoticeEvent，由 WebSocket 层监听并推送
 */
@Service
public class GlobalNoticeService implements IGlobalNoticeService {

    private static final Logger logger = LoggerFactory.getLogger(GlobalNoticeService.class);

    private final ApplicationEventPublisher eventPublisher;

    public GlobalNoticeService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void notify(String userId, GlobalNoticeTypeEnum type, String subtype, Map<String, Object> content) {
        try {
            GlobalNoticeMessage message = new GlobalNoticeMessage(type != null ? type.getCode() : null, subtype);
            if (content != null) {
                message.getContent().putAll(content);
            }
            eventPublisher.publishEvent(new GlobalNoticeEvent(userId, message));
        } catch (Exception e) {
            logger.warn("发布全局通知失败: type={}, subtype={}, error={}", type, subtype, e.getMessage());
        }
    }
}
