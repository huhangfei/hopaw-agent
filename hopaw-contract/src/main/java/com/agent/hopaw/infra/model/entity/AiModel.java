package com.agent.hopaw.infra.model.entity;

public class AiModel {
    private Long id;
    private Long providerId;
    private String modelName;
    private String modelAlias;
    /**
     * 最大上下文（字节），必填
     */
    private Long maxContextTokens;
    private String capabilities;
    private Boolean verified;
    private String extParams;
    /** 是否支持思考模式 */
    private Boolean supportThinking;
    /** 支持的思考等级（逗号分隔，如 "low,medium,high"），仅 supportThinking=true 时有意义 */
    private String supportedThinkingLevels;

    private String createTime;



    public AiModel() {}

    public AiModel(Long providerId, String modelName, String capabilities, Boolean verified) {
        this.providerId = providerId;
        this.modelName = modelName;
        this.capabilities = capabilities;
        this.verified = verified;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProviderId() {
        return providerId;
    }

    public void setProviderId(Long providerId) {
        this.providerId = providerId;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getModelAlias() {
        return modelAlias;
    }

    public void setModelAlias(String modelAlias) {
        this.modelAlias = modelAlias;
    }

    public Long getMaxContextTokens() {
        return maxContextTokens;
    }

    public void setMaxContextTokens(Long maxContextTokens) {
        this.maxContextTokens = maxContextTokens;
    }

    public String getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(String capabilities) {
        this.capabilities = capabilities;
    }

    public String[] getCapabilitiesArray() {
        if (capabilities == null || capabilities.isEmpty()) {
            return new String[0];
        }
        return capabilities.split(",");
    }

    public Boolean getVerified() {
        return verified;
    }

    public void setVerified(Boolean verified) {
        this.verified = verified;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public String getExtParams() {
        return extParams;
    }

    public void setExtParams(String extParams) {
        this.extParams = extParams;
    }

    public Boolean getSupportThinking() {
        return supportThinking;
    }

    public void setSupportThinking(Boolean supportThinking) {
        this.supportThinking = supportThinking;
    }

    public String getSupportedThinkingLevels() {
        return supportedThinkingLevels;
    }

    public void setSupportedThinkingLevels(String supportedThinkingLevels) {
        this.supportedThinkingLevels = supportedThinkingLevels;
    }

    /**
     * 获取支持的思考等级数组（逗号分隔字符串转数组）
     */
    public String[] getSupportedThinkingLevelsArray() {
        if (supportedThinkingLevels == null || supportedThinkingLevels.isEmpty()) {
            return new String[0];
        }
        return supportedThinkingLevels.split(",");
    }
}
