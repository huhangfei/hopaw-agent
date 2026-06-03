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
                userId = httpRequest.getParameter("userId");
            }
            // userId 是必填，未登录则拒绝握手
            if (userId == null || userId.isEmpty()) {
                return false;
            }
            attributes.put("userId", userId);

            // agentId 是可选：Chat WS 不需要，Avatar WS 需要（handler 内部自行判定）
            String agentIdStr = httpRequest.getParameter("agentId");
            if (agentIdStr != null && !agentIdStr.isEmpty()) {
                try {
                    attributes.put("agentId", Long.parseLong(agentIdStr));
                } catch (NumberFormatException e) {
                    // 非法 agentId 不阻塞连接，handler 收到时再处理
                }
            }
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
    }
}
