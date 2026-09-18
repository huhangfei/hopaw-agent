package com.agent.hopaw.util;

import com.agent.hopaw.constant.DefaultUser;
import com.agent.hopaw.infra.model.entity.Account;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * 当前登录用户工具
 * <p>统一从 HttpSession 中读取登录用户标识，替代之前硬编码的 DefaultUser.USER。</p>
 */
public final class CurrentUser {

    public static final String SESSION_KEY = "currentUserId";
    public static final String SESSION_ACCOUNT_KEY = "currentAccount";
    public static final String LOGIN_REDIRECT_ATTR = "loginRedirect";

    private CurrentUser() {}

    /**
     * 从 request 中读取当前登录用户编号，未登录则回退到 DefaultUser（兼容历史调用）。
     */
    public static String require(HttpServletRequest request) {
        String userId = fromSession(request);
        if (userId == null || userId.isEmpty()) {
            return DefaultUser.USER;
        }
        return userId;
    }

    /**
     * 从 session 读取当前登录用户编号，未登录返回 null。
     */
    public static String fromSession(HttpServletRequest request) {
        if (request == null) return null;
        HttpSession session = request.getSession(false);
        if (session == null) return null;
        Object value = session.getAttribute(SESSION_KEY);
        return value == null ? null : value.toString();
    }

    /**
     * 写入当前登录用户
     */
    public static void set(HttpServletRequest request, String userId, Account account) {
        if (request == null) return;
        HttpSession session = request.getSession(true);
        session.setAttribute(SESSION_KEY, userId);
        if (account != null) {
            session.setAttribute(SESSION_ACCOUNT_KEY, account);
        }
    }

    /**
     * 清除当前登录用户
     */
    public static void clear(HttpServletRequest request) {
        if (request == null) return;
        HttpSession session = request.getSession(false);
        if (session == null) return;
        session.removeAttribute(SESSION_KEY);
        session.removeAttribute(SESSION_ACCOUNT_KEY);
    }

    /**
     * 是否已登录
     */
    public static boolean isLogin(HttpServletRequest request) {
        String userId = fromSession(request);
        return userId != null && !userId.isEmpty();
    }
}
