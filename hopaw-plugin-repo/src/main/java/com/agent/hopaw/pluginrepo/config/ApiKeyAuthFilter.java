package com.agent.hopaw.pluginrepo.config;

import com.agent.hopaw.pluginrepo.entity.ApiKey;
import com.agent.hopaw.pluginrepo.entity.User;
import com.agent.hopaw.pluginrepo.service.ApiKeyService;
import com.agent.hopaw.pluginrepo.service.UserService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;

public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final ApiKeyService apiKeyService;
    private final UserService userService;

    public ApiKeyAuthFilter(ApiKeyService apiKeyService, UserService userService) {
        this.apiKeyService = apiKeyService;
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = request.getParameter("api_key");
        }

        if (apiKey == null || apiKey.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"Missing API key (X-API-Key header or api_key query parameter)\"}");
            return;
        }

        ApiKey key = apiKeyService.findByKeyValue(apiKey);
        if (key == null || key.isLocked() || key.isExpired()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"Invalid, locked, or expired API key\"}");
            return;
        }

        User user = userService.findById(key.getUserId());
        if (user == null || user.isLocked()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"User not found or locked\"}");
            return;
        }

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                user, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
        SecurityContextHolder.getContext().setAuthentication(auth);

        filterChain.doFilter(request, response);
    }
}