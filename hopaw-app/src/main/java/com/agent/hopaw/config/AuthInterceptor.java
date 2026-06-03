package com.agent.hopaw.config;

import com.agent.hopaw.util.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;

/**
 * 登录态拦截器：未登录的请求跳转至 /login。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthInterceptor.class);

    private static final Set<String> PAGE_WHITELIST = Set.of(
            "/login"
    );

    private static final Set<String> API_WHITELIST = Set.of(
            "/api/auth/accounts",
            "/api/auth/login",
            "/api/auth/logout"
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String path = request.getRequestURI();
        // 静态资源 / WebSocket / 错误页一律放行
        if (isStatic(path) || path.startsWith("/ws/") || path.startsWith("/error")) {
            return true;
        }
        // 白名单接口/页面放行
        if (PAGE_WHITELIST.contains(path) || API_WHITELIST.contains(path)) {
            return true;
        }

        if (!CurrentUser.isLogin(request)) {
            // 区分页面请求与 API 请求
            if (isApiRequest(path) || isJsonRequest(request)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":401,\"msg\":\"未登录\"}");
                return false;
            }
            // 页面请求：携带来源路径以便登录后回跳
            String query = request.getQueryString();
            String target = path + (query == null ? "" : "?" + query);
            response.sendRedirect(request.getContextPath() + "/login?redirect=" + java.net.URLEncoder.encode(target, "UTF-8"));
            return false;
        }
        return true;
    }

    private boolean isStatic(String path) {
        return path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/icons/")
                || path.startsWith("/images/") || path.startsWith("/static/") || path.startsWith("/test/")
                || path.endsWith(".css") || path.endsWith(".js") || path.endsWith(".svg")
                || path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".ico")
                || path.endsWith(".wav") || path.endsWith(".mp3");
    }

    private boolean isApiRequest(String path) {
        return path.startsWith("/api/");
    }

    private boolean isJsonRequest(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        if (accept == null) return false;
        return accept.contains("application/json") && !accept.contains("text/html");
    }
}
