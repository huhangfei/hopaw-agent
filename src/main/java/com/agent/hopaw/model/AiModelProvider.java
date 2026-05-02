package com.agent.hopaw.model;

import com.alibaba.fastjson2.JSON;

public class AiModelProvider {
    private Long id;
    private String name;
    private String provider;
    private String type;
    private String url;
    private String apiKey;
    private String icon;
    private String sdkName;
    private String extParams;
    private String createTime;

    public AiModelProvider() {}

    public AiModelProvider(String name, String provider, String type, String url, String apiKey) {
        this.name = name;
        this.provider = provider;
        this.type = type;
        this.url = url;
        this.apiKey = apiKey;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getSdkName() {
        return sdkName;
    }

    public void setSdkName(String sdkName) {
        this.sdkName = sdkName;
    }

    public String getExtParams() {
        return extParams;
    }

    public void setExtParams(String extParams) {
        this.extParams = extParams;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public AiModelExtParams getAiModelExtParamsObj() {
        try {
            return JSON.parseObject(extParams, AiModelExtParams.class);
        } catch (Exception e) {
            return null;
        }
    }
}
