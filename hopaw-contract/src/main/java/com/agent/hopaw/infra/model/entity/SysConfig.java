package com.agent.hopaw.infra.model.entity;

public class SysConfig {
    private Long id;
    private String configKey;
    private String configValue;
    private String description;
    /** 是否加密存储：1=加密（{AES}密文），0/null=明文。读取时按此标记决定是否解密 */
    private Integer isEncrypted;
    private String createTime;
    private String updateTime;

    public SysConfig() {}

    public SysConfig(String configKey, String configValue, String description) {
        this.configKey = configKey;
        this.configValue = configValue;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getConfigKey() {
        return configKey;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigValue() {
        return configValue;
    }

    public void setConfigValue(String configValue) {
        this.configValue = configValue;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getIsEncrypted() {
        return isEncrypted;
    }

    public void setIsEncrypted(Integer isEncrypted) {
        this.isEncrypted = isEncrypted;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public String getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(String updateTime) {
        this.updateTime = updateTime;
    }
}
