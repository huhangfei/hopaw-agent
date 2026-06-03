package com.agent.hopaw.config;

import com.agent.hopaw.infra.model.entity.Account;
import com.agent.hopaw.infra.service.AccountService;
import com.agent.hopaw.util.AccountAvatar;
import com.agent.hopaw.util.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class ThemeInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(ThemeInterceptor.class);

    public static final String THEME_COOKIE_NAME = "theme";
    public static final String THEME_MODEL_ATTRIBUTE = "currentTheme";
    public static final String THEME_REQUEST_ATTRIBUTE = "_currentTheme";
    public static final String THEME_DARK = "dark";
    public static final String THEME_LIGHT = "light";

    public static final String MENU_COLLAPSED_COOKIE = "menuCollapsed";
    public static final String MENU_COLLAPSED_MODEL_KEY = "menuCollapsed";

    private final AccountService accountService;

    public ThemeInterceptor(AccountService accountService) {
        this.accountService = accountService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String theme = getThemeFromCookie(request);
        request.setAttribute(THEME_REQUEST_ATTRIBUTE, theme);
        logger.debug("ThemeInterceptor preHandle: theme={}", theme);
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        if (modelAndView != null && modelAndView.getViewName() != null) {
            String theme = (String) request.getAttribute(THEME_REQUEST_ATTRIBUTE);
            if (theme == null) {
                theme = getThemeFromCookie(request);
            }
            modelAndView.addObject(THEME_MODEL_ATTRIBUTE, theme);
            modelAndView.addObject("activePage", resolveActivePage(request));
            modelAndView.addObject(MENU_COLLAPSED_MODEL_KEY, isMenuCollapsed(request));

            // 注入当前登录用户信息供布局使用
            String userId = CurrentUser.fromSession(request);
            if (userId != null) {
                modelAndView.addObject("currentUserId", userId);
                Account account = accountService.getByUserId(userId);
                String nickname = account != null ? account.getNickname() : null;
                String displayName = (nickname != null && !nickname.isEmpty())
                        ? nickname
                        : (account != null && account.getUsername() != null && !account.getUsername().isEmpty()
                            ? account.getUsername()
                            : userId);
                modelAndView.addObject("currentNickname", displayName);
                modelAndView.addObject("currentInitial", AccountAvatar.initial(displayName));
            }
            logger.debug("ThemeInterceptor postHandle: modelAndView={}, theme={}", modelAndView.getViewName(), theme);
        }
    }

    private String resolveActivePage(HttpServletRequest request) {
        String path = request.getRequestURI();
        switch (path) {
            case "/":               return "index";
            case "/models":         return "models";
            case "/memory-manage":  return "memory-manage";
            case "/vector-history": return "vector-history";
            case "/tools":          return "tools";
            case "/tools/plugin-store":   return "tools";
            case "/tasks":          return "tasks";
            case "/token-usage":    return "token-usage";
            case "/settings":       return "settings";
            case "/skills":         return "skills";
            case "/accounts":       return "accounts";
            case "/agents":         return "agents";
            case "/login":          return "login";
            default:                return "";
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

    private boolean isMenuCollapsed(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (MENU_COLLAPSED_COOKIE.equals(cookie.getName())) {
                    return "true".equals(cookie.getValue());
                }
            }
        }
        return false;
    }
}
