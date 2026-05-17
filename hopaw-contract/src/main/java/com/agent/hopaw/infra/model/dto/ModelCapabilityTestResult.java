package com.agent.hopaw.infra.model.dto;

import com.agent.hopaw.infra.constant.ModelCapabilityEnum;

import java.util.List;

public class ModelCapabilityTestResult {
    private boolean verified;
    private List<ModelCapabilityEnum> capabilities;
    private String message;

    public ModelCapabilityTestResult() {}

    public ModelCapabilityTestResult(boolean verified, List<ModelCapabilityEnum> capabilities, String message) {
        this.verified = verified;
        this.capabilities = capabilities;
        this.message = message;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public List<ModelCapabilityEnum> getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(List<ModelCapabilityEnum> capabilities) {
        this.capabilities = capabilities;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
