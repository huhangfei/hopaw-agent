package com.agent.hopaw.constant;

public enum ModelProviderEnum {
    OPENAI("openai", "OpenAI", "https://api.openai.com/v1", "/icons/openai.svg"),
    ANTHROPIC("anthropic", "Anthropic", "https://api.anthropic.com", "/icons/anthropic.svg"),
    GOOGLE("google", "Google", "https://generativelanguage.googleapis.com", "/icons/google.svg"),
    DEEPSEEK("deepseek", "DeepSeek", "https://api.deepseek.com", "/icons/deepseek.svg"),
    QWEN("qwen", "通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", "/icons/qwen.svg"),
    ZHIPU("zhipu", "智谱AI", "https://open.bigmodel.cn/api/paas/v4", "/icons/zhipu.svg"),
    MOONSHOT("moonshot", "月之暗面", "https://api.moonshot.cn/v1", "/icons/moonshot.svg"),
    MINIMAX("minimax", "MiniMax", "https://api.minimax.chat/v1", "/icons/minimax.svg");

    private final String code;
    private final String name;
    private final String defaultUrl;
    private final String icon;

    ModelProviderEnum(String code, String name, String defaultUrl, String icon) {
        this.code = code;
        this.name = name;
        this.defaultUrl = defaultUrl;
        this.icon = icon;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDefaultUrl() {
        return defaultUrl;
    }

    public String getIcon() {
        return icon;
    }

    public static ModelProviderEnum fromCode(String code) {
        for (ModelProviderEnum provider : values()) {
            if (provider.code.equals(code)) {
                return provider;
            }
        }
        return null;
    }
}
