package com.agent.hopaw.avatar.model;

public class AvatarEvent {
    public static final String TYPE_ACTION = "avatar_action";
    public static final String TYPE_INTIMACY = "avatar_intimacy";
    public static final String TYPE_INTIMACY_UPDATE = "avatar_intimacy_update";
    public static final String TYPE_PROACTIVE_MESSAGE = "avatar_proactive_message";

    private String type;
    private String userId;
    private String action;
    private String actionDescription;
    private String message;
    private UserIntimacyInfo intimacyInfo;
    private Boolean autoShow;
    private Boolean dismissible;

    public AvatarEvent() {
    }

    public static AvatarEvent action(String userId, AvatarAction action) {
        return action(userId, action, null);
    }

    public static AvatarEvent action(String userId, AvatarAction action, String message) {
        AvatarEvent event = new AvatarEvent();
        event.type = TYPE_ACTION;
        event.userId = userId;
        event.action = action.getCode();
        event.actionDescription = action.getDescription();
        event.message = message;
        event.autoShow = Boolean.TRUE;
        event.dismissible = Boolean.FALSE;
        return event;
    }

    public static AvatarEvent intimacyUp(String userId, UserIntimacyInfo intimacyInfo) {
        AvatarEvent event = new AvatarEvent();
        event.type = TYPE_INTIMACY;
        event.userId = userId;
        event.action = AvatarAction.INTIMACY_UP.getCode();
        event.actionDescription = AvatarAction.INTIMACY_UP.getDescription();
        event.intimacyInfo = intimacyInfo;
        event.autoShow = Boolean.TRUE;
        event.dismissible = Boolean.FALSE;
        return event;
    }

    public static AvatarEvent intimacyUpdate(String userId, UserIntimacyInfo intimacyInfo) {
        AvatarEvent event = new AvatarEvent();
        event.type = TYPE_INTIMACY_UPDATE;
        event.userId = userId;
        event.action = "intimacy_update";
        event.actionDescription = "亲密度进度更新";
        event.intimacyInfo = intimacyInfo;
        event.autoShow = Boolean.FALSE;
        event.dismissible = Boolean.FALSE;
        return event;
    }

    public static AvatarEvent proactiveMessage(String userId, String message) {
        AvatarEvent event = new AvatarEvent();
        event.type = TYPE_PROACTIVE_MESSAGE;
        event.userId = userId;
        event.action = "proactive_message";
        event.actionDescription = "主动关怀";
        event.message = message;
        event.autoShow = Boolean.FALSE;
        event.dismissible = Boolean.TRUE;
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

    public Boolean getAutoShow() {
        return autoShow;
    }

    public void setAutoShow(Boolean autoShow) {
        this.autoShow = autoShow;
    }

    public Boolean getDismissible() {
        return dismissible;
    }

    public void setDismissible(Boolean dismissible) {
        this.dismissible = dismissible;
    }
}
