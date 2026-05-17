package com.agent.hopaw.infra.model.entity;

public class AiModel {
    private Long id;
    private Long providerId;
    private String modelName;
    private String capabilities;
    private Boolean verified;
    private String extParams;

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
}
