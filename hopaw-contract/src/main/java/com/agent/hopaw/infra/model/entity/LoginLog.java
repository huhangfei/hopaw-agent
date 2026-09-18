package com.agent.hopaw.infra.model.entity;

/**
 * 登录日志
 */
public class LoginLog {

    private Long id;
    /** 用户编号 */
    private String userId;
    /** 用户名称 */
    private String username;
    /** 登录IP */
    private String ip;
    /** 登录结果：success/failed */
    private String result;
    /** 失败原因 */
    private String failReason;
    private String createTime;

    public LoginLog() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public String getFailReason() { return failReason; }
    public void setFailReason(String failReason) { this.failReason = failReason; }

    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }
}
