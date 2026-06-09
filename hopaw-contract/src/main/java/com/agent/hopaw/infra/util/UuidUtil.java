package com.agent.hopaw.infra.util;

import java.util.UUID;

public class UuidUtil {
    /**
     * 生成标准的带横杠 UUID (36位)
     * 格式示例：a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d
     */
    public static String generateStandardUUID() {
        return UUID.randomUUID().toString();
    }

    /**
     * 生成不带横杠的 UUID (32位)
     * 适用于数据库主键或业务唯一标识
     * 格式示例：a1b2c3d4e5f64a7b8c9d0e1f2a3b4c5d
     */
    public static String generateSimpleUUID() {
        // 原生方式需要手动替换连字符，会产生额外的字符串处理开销
        return UUID.randomUUID().toString().replaceAll("-", "");
    }
}
