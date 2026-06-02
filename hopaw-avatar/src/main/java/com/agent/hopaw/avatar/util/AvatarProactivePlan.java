package com.agent.hopaw.avatar.util;

public class AvatarProactivePlan {

    private boolean needRemind;
    private String reason;
    private String message;

    public AvatarProactivePlan() {
    }

    public AvatarProactivePlan(boolean needRemind, String reason, String message) {
        this.needRemind = needRemind;
        this.reason = reason;
        this.message = message;
    }

    public static AvatarProactivePlan skip(String reason) {
        return new AvatarProactivePlan(false, reason, null);
    }

    public static AvatarProactivePlan remind(String reason, String message) {
        return new AvatarProactivePlan(true, reason, message);
    }

    public boolean isNeedRemind() {
        return needRemind;
    }

    public void setNeedRemind(boolean needRemind) {
        this.needRemind = needRemind;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
