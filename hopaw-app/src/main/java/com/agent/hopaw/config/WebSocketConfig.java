package com.agent.hopaw.config;

import com.agent.hopaw.avatar.websocket.AvatarWebSocketHandler;
import com.agent.hopaw.websocket.ChatWebSocketHandler;
import com.agent.hopaw.websocket.NoticeWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;
    private final AvatarWebSocketHandler avatarWebSocketHandler;
    private final NoticeWebSocketHandler noticeWebSocketHandler;

    public WebSocketConfig(ChatWebSocketHandler chatWebSocketHandler, AvatarWebSocketHandler avatarWebSocketHandler,
                           NoticeWebSocketHandler noticeWebSocketHandler) {
        this.chatWebSocketHandler = chatWebSocketHandler;
        this.avatarWebSocketHandler = avatarWebSocketHandler;
        this.noticeWebSocketHandler = noticeWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, "/ws/chat")
                .addInterceptors(new WSHandshakeInterceptor())
                .setAllowedOrigins("*");

        registry.addHandler(avatarWebSocketHandler, "/ws/avatar")
                .addInterceptors(new WSHandshakeInterceptor())
                .setAllowedOrigins("*");

        // 全局通知通道：所有页面订阅，接收公共消息推送
        registry.addHandler(noticeWebSocketHandler, "/ws/notice")
                .addInterceptors(new WSHandshakeInterceptor())
                .setAllowedOrigins("*");
    }
}
