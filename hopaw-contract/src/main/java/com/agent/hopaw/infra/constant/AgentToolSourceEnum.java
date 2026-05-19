package com.agent.hopaw.infra.constant;

public enum AgentToolSourceEnum {
    //内置工具
    BUILT_IN("built_in", "内置工具"),
    //插件
    PLUGIN("plugin", "插件");

    private String code;
    private String description;

    AgentToolSourceEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
