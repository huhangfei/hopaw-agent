package com.agent.hopaw.infra.model.dto;

/**
 *
 * @author hhf
 */
public class AgentExecutorResult {
    private Boolean success;
    private String message;

    public AgentExecutorResult(Boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
