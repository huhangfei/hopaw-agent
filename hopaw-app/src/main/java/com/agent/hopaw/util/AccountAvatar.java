package com.agent.hopaw.util;

/**
 * 登录页头像首字母工具
 */
public final class AccountAvatar {

    private AccountAvatar() {}

    public static String initial(String name) {
        if (name == null || name.isEmpty()) {
            return "?";
        }
        // 取首个非空白字符
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!Character.isWhitespace(c)) {
                return String.valueOf(c).toUpperCase();
            }
        }
        return "?";
    }
}
