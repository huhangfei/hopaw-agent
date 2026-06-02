package com.agent.hopaw.avatar.model;

public class AvatarEvent {
    private String type;
    private String userId;
    private String action;
    private String actionDescription;
    private String message;
    private UserIntimacyInfo intimacyInfo;

    public AvatarEvent() {
    }

    public static AvatarEvent action(String userId, AvatarAction action) {
        return action(userId, action, null);
    }

    public static AvatarEvent action(String userId, AvatarAction action, String message) {
        AvatarEvent event = new AvatarEvent();
        event.type = "avatar_action";
        event.userId = userId;
        event.action = action.getCode();
        event.actionDescription = action.getDescription();
        event.message = message;
        return event;
    }

    public static AvatarEvent intimacyUp(String userId, UserIntimacyInfo intimacyInfo) {
        AvatarEvent event = new AvatarEvent();
        event.type = "avatar_intimacy";
        event.userId = userId;
        event.action = AvatarAction.INTIMACY_UP.getCode();
        event.actionDescription = AvatarAction.INTIMACY_UP.getDescription();
        event.intimacyInfo = intimacyInfo;
        return event;
    }

    public static AvatarEvent intimacyUpdate(String userId, UserIntimacyInfo intimacyInfo) {
        AvatarEvent event = new AvatarEvent();
        event.type = "avatar_intimacy_update";
        event.userId = userId;
        event.action = "intimacy_update";
        event.actionDescription = "亲密度进度更新";
        event.intimacyInfo = intimacyInfo;
        return event;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getActionDescription() {
        return actionDescription;
    }

    public void setActionDescription(String actionDescription) {
        this.actionDescription = actionDescription;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public UserIntimacyInfo getIntimacyInfo() {
        return intimacyInfo;
    }

    public void setIntimacyInfo(UserIntimacyInfo intimacyInfo) {
        this.intimacyInfo = intimacyInfo;
    }
}
