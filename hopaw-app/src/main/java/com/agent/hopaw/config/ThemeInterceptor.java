package com.agent.hopaw.config;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

@Component
public class ThemeInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(ThemeInterceptor.class);

    public static final String THEME_COOKIE_NAME = "theme";
    public static final String THEME_MODEL_ATTRIBUTE = "currentTheme";
    public static final String THEME_REQUEST_ATTRIBUTE = "_currentTheme";
    public static final String THEME_DARK = "dark";
    public static final String THEME_LIGHT = "light";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String theme = getThemeFromCookie(request);
        request.setAttribute(THEME_REQUEST_ATTRIBUTE, theme);
        logger.debug("ThemeInterceptor preHandle: theme={}", theme);
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        if (modelAndView != null) {
            String theme = (String) request.getAttribute(THEME_REQUEST_ATTRIBUTE);
            if (theme == null) {
                theme = getThemeFromCookie(request);
            }
            modelAndView.addObject(THEME_MODEL_ATTRIBUTE, theme);
            logger.debug("ThemeInterceptor postHandle: modelAndView={}, theme={}", modelAndView.getViewName(), theme);
        }
    }

    private String getThemeFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (THEME_COOKIE_NAME.equals(cookie.getName())) {
                    logger.debug("Found theme cookie: value={}", cookie.getValue());
                    return THEME_DARK.equals(cookie.getValue()) ? THEME_DARK : THEME_LIGHT;
                }
            }
        }
        logger.debug("No theme cookie found, returning default: light");
        return THEME_LIGHT;
    }
}
