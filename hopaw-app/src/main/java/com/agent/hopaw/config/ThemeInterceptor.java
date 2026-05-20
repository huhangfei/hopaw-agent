package com.agent.hopaw.config;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

@Component
public class ThemeInterceptor implements HandlerInterceptor {

    public static final String THEME_COOKIE_NAME = "theme";
    public static final String THEME_MODEL_ATTRIBUTE = "currentTheme";
    public static final String THEME_DARK = "dark";
    public static final String THEME_LIGHT = "light";

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        if (modelAndView != null) {
            String theme = getThemeFromCookie(request);
            modelAndView.addObject(THEME_MODEL_ATTRIBUTE, theme);
        }
    }

    private String getThemeFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (THEME_COOKIE_NAME.equals(cookie.getName())) {
                    return THEME_DARK.equals(cookie.getValue()) ? THEME_DARK : THEME_LIGHT;
                }
            }
        }
        return THEME_LIGHT;
    }
}
