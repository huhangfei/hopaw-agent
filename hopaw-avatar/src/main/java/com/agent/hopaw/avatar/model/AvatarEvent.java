package com.agent.hopaw.avatar.model;

public class AvatarEvent {
    public static final String TYPE_ACTION = "avatar_action";
    public static final String TYPE_INTIMACY = "avatar_intimacy";
    public static final String TYPE_INTIMACY_UPDATE = "avatar_intimacy_update";
    public static final String TYPE_PROACTIVE_MESSAGE = "avatar_proactive_message";
    public static final String TYPE_MOVE = "avatar_move";
    public static final String TYPE_CHANGE_MODEL = "avatar_change_model";

    private String type;
    private String userId;
    private Long agentId;
    private String action;
    private String actionDescription;
    private String message;
    private UserIntimacyInfo intimacyInfo;
    private Boolean autoShow;
    private Boolean dismissible;
    private Integer targetX;
    private Integer targetY;
    private Long durationMs;
    private String soundFile;

    public AvatarEvent() {
    }

    public static AvatarEvent action(String userId, Long agentId, AvatarAction action) {
        return action(userId, agentId, action, action.getRandomPhrase());
    }

    public static AvatarEvent action(String userId, Long agentId, AvatarAction action, String message) {
        AvatarEvent event = new AvatarEvent();
        event.type = TYPE_ACTION;
        event.userId = userId;
        event.agentId = agentId;
        event.action = action.getCode();
        event.actionDescription = action.getDescription();
        event.message = message;
        event.autoShow = Boolean.TRUE;
        event.dismissible = Boolean.FALSE;
        event.soundFile = action.getSoundFile();
        return event;
    }

    public static AvatarEvent intimacyUp(String userId, Long agentId, UserIntimacyInfo intimacyInfo) {
        AvatarEvent event = new AvatarEvent();
        event.type = TYPE_INTIMACY;
        event.userId = userId;
        event.agentId = agentId;
        event.action = AvatarAction.INTIMACY_UP.getCode();
        event.actionDescription = AvatarAction.INTIMACY_UP.getDescription();
        event.intimacyInfo = intimacyInfo;
        event.autoShow = Boolean.TRUE;
        event.dismissible = Boolean.FALSE;
        event.soundFile = AvatarAction.INTIMACY_UP.getSoundFile();
        return event;
    }

    public static AvatarEvent intimacyUpdate(String userId, Long agentId, UserIntimacyInfo intimacyInfo) {
        AvatarEvent event = new AvatarEvent();
        event.type = TYPE_INTIMACY_UPDATE;
        event.userId = userId;
        event.agentId = agentId;
        event.action = "intimacy_update";
        event.actionDescription = "亲密度进度更新";
        event.intimacyInfo = intimacyInfo;
        event.autoShow = Boolean.FALSE;
        event.dismissible = Boolean.FALSE;
        return event;
    }

    public static AvatarEvent proactiveMessage(String userId, Long agentId, String message) {
        AvatarEvent event = new AvatarEvent();
        event.type = TYPE_PROACTIVE_MESSAGE;
        event.userId = userId;
        event.agentId = agentId;
        event.action = "proactive_message";
        event.actionDescription = "主动关怀";
        event.message = message;
        event.autoShow = Boolean.FALSE;
        event.dismissible = Boolean.TRUE;
        event.soundFile = AvatarAction.SOUND_FILE_PROACTIVE_MESSAGE;
        return event;
    }

    public static AvatarEvent move(String userId, Long agentId, int targetX, int targetY, long durationMs) {
        AvatarEvent event = new AvatarEvent();
        event.type = TYPE_MOVE;
        event.userId = userId;
        event.agentId = agentId;
        event.action = "move";
        event.actionDescription = "移动";
        event.targetX = targetX;
        event.targetY = targetY;
        event.durationMs = durationMs;
        event.autoShow = Boolean.FALSE;
        event.dismissible = Boolean.FALSE;
        event.soundFile = AvatarAction.SOUND_FILE_MOVE;
        return event;
    }

    public static AvatarEvent changeModel(String userId, Long agentId) {
        AvatarEvent event = new AvatarEvent();
        event.type = TYPE_CHANGE_MODEL;
        event.userId = userId;
        event.agentId = agentId;
        event.action = "change_model";
        event.actionDescription = "换装";
        event.autoShow = Boolean.FALSE;
        event.dismissible = Boolean.FALSE;
        event.soundFile = AvatarAction.SOUND_FILE_CHANGE_MODEL;
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

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
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

    public Integer getTargetX() {
        return targetX;
    }

    public void setTargetX(Integer targetX) {
        this.targetX = targetX;
    }

    public Integer getTargetY() {
        return targetY;
    }

    public void setTargetY(Integer targetY) {
        this.targetY = targetY;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public String getSoundFile() {
        return soundFile;
    }

    public void setSoundFile(String soundFile) {
        this.soundFile = soundFile;
    }
}
