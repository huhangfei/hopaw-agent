package com.agent.hopaw.constant;

public enum ModelProviderEnum {
    OPENAI("openai", "OpenAI", "openai", "https://api.openai.com/v1", "/icons/openai.svg"),
    ANTHROPIC("anthropic", "Anthropic", "anthropic", "https://api.anthropic.com", "/icons/anthropic.svg"),
    GOOGLE("google", "Google", "google", "https://generativelanguage.googleapis.com", "/icons/google.svg"),
    DEEPSEEK("deepseek", "DeepSeek", "deepseek", "https://api.deepseek.com", "/icons/deepseek.svg"),
    QWEN("qwen", "通义千问", "qwen", "https://dashscope.aliyuncs.com/compatible-mode/v1", "/icons/qwen.svg"),
    ZHIPU("zhipu", "智谱AI", "zhipu", "https://open.bigmodel.cn/api/paas/v4", "/icons/zhipu.svg"),
    MOONSHOT("moonshot", "月之暗面", "moonshot", "https://api.moonshot.cn/v1", "/icons/moonshot.svg"),
    MINIMAX("minimax", "MiniMax", "minimax", "https://api.minimax.chat/v1", "/icons/minimax.svg");

    private final String code;
    private final String name;
    private final String sdkName;
    private final String defaultUrl;
    private final String icon;

    ModelProviderEnum(String code, String name, String sdkName, String defaultUrl, String icon) {
        this.code = code;
        this.name = name;
        this.sdkName = sdkName;
        this.defaultUrl = defaultUrl;
        this.icon = icon;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getSdkName() {
        return sdkName;
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
