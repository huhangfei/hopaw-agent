package com.agent.hopaw.config;

import com.agent.hopaw.util.CurrentUser;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

public class WSHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        if (request instanceof ServletServerHttpRequest) {
            ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
            HttpServletRequest httpRequest = servletRequest.getServletRequest();
            String userId = CurrentUser.fromSession(httpRequest);
            if (userId == null || userId.isEmpty()) {
                // WebSocket 握手通常会复用浏览器 cookie/session，这里若仍未读到
                // 则尝试从请求参数 ?userId= 兜底（前端可附带）
                userId = httpRequest.getParameter("userId");
            }
            if (userId == null || userId.isEmpty()) {
                // 兜底：未登录不允许建立 ws
                return false;
            }
            attributes.put("userId", userId);
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
    }
}
