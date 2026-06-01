package com.agent.hopaw.config;

import com.agent.hopaw.avatar.websocket.AvatarWebSocketHandler;
import com.agent.hopaw.websocket.ChatWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;
    private final AvatarWebSocketHandler avatarWebSocketHandler;

    public WebSocketConfig(ChatWebSocketHandler chatWebSocketHandler, AvatarWebSocketHandler avatarWebSocketHandler) {
        this.chatWebSocketHandler = chatWebSocketHandler;
        this.avatarWebSocketHandler = avatarWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, "/ws/chat")
                .addInterceptors(new WSHandshakeInterceptor())
                .setAllowedOrigins("*");

        registry.addHandler(avatarWebSocketHandler, "/ws/avatar")
                .addInterceptors(new WSHandshakeInterceptor())
                .setAllowedOrigins("*");
    }
}
