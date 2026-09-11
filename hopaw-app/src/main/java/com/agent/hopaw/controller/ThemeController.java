package com.agent.hopaw.controller;

import com.agent.hopaw.config.ThemeInterceptor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

@Controller
public class ThemeController {

    @PostMapping("/api/theme")
    public String toggleTheme(
            @RequestParam(defaultValue = "light") String theme,
            HttpServletResponse response) {
        
        Cookie cookie = new Cookie(ThemeInterceptor.THEME_COOKIE_NAME, theme);
        cookie.setMaxAge(365 * 24 * 60 * 60);
        cookie.setPath("/");
        response.addCookie(cookie);
        
        return "redirect:/";
    }
}
