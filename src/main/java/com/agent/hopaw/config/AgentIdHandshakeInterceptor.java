package com.agent.hopaw.config;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

public class AgentIdHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        if (request instanceof ServletServerHttpRequest) {
            ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
            String path = servletRequest.getServletRequest().getRequestURI();
            String agentId = extractAgentIdFromPath(path);
            if (agentId != null) {
                attributes.put("agentId", agentId);
            }
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
    }

    private String extractAgentIdFromPath(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length; i++) {
            if ("ws".equals(parts[i]) && i + 2 < parts.length && "chat".equals(parts[i + 1])) {
                return parts[i + 2];
            }
        }
        if (parts.length > 0 && parts[parts.length - 1].matches("\\d+")) {
            return parts[parts.length - 1];
        }
        return null;
    }
}
