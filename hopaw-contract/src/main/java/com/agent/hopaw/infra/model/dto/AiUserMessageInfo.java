package com.agent.hopaw.infra.model.dto;

import java.util.List;

/**
 * 用户消息通知：用户在会话页发送消息并入库后，向前端推送的回显消息（多端实时可见）。
 * 需在 loading（received）消息之前推送，前端收到后按消息列表逻辑渲染。
 */
public class AiUserMessageInfo extends AiMessageBaseInfo {
    /** 用户消息附带的文件（前端按独立消息渲染，仅 image 类型） */
    private List<AttachmentFile> files;

    public AiUserMessageInfo() {
        super("user_message");
    }

    public List<AttachmentFile> getFiles() {
        return files;
    }

    public void setFiles(List<AttachmentFile> files) {
        this.files = files;
    }

    public static AiUserMessageInfo of(String sessionId, String requestId, String content, List<AttachmentFile> files) {
        AiUserMessageInfo info = new AiUserMessageInfo();
        info.sessionId(sessionId);
        info.requestId(requestId);
        info.content(content);
        info.files = files;
        return info;
    }
}