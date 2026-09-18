package com.agent.hopaw.infra.model.dto;

/**
 * 项目自动迭代执行结果
 * @author hhf
 */
public class ProjectIterateResult {
    /** 是否执行成功（含跳过场景返回 false 并附原因） */
    private boolean success;
    /** 执行结果说明或失败原因 */
    private String message;

    public ProjectIterateResult() {
    }

    public ProjectIterateResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public static ProjectIterateResult ok(String message) {
        return new ProjectIterateResult(true, message);
    }

    public static ProjectIterateResult fail(String reason) {
        return new ProjectIterateResult(false, reason);
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
