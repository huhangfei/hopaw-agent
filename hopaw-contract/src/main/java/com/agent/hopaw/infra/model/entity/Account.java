package com.agent.hopaw.infra.model.entity;

/**
 * 账户信息
 */
public class Account {

    private Long id;
    /** 用户编号（业务唯一标识） */
    private String userId;
    /** 用户名称 */
    private String username;
    /** 用户昵称 */
    private String nickname;
    /** 状态：1 启用，0 禁用 */
    private Integer status;
    /** 是否启用密码：1 启用，0 未启用 */
    private Integer passwordEnabled;
    /** 登录密码（BCrypt 加密存储） */
    private String password;
    /** 备注 */
    private String remark;
    private String createTime;
    private String updateTime;

    public Account() {}

    public Account(String userId, String username, String nickname, Integer status, String remark) {
        this.userId = userId;
        this.username = username;
        this.nickname = nickname;
        this.status = status;
        this.remark = remark;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public Integer getPasswordEnabled() { return passwordEnabled; }
    public void setPasswordEnabled(Integer passwordEnabled) { this.passwordEnabled = passwordEnabled; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }

    public String getUpdateTime() { return updateTime; }
    public void setUpdateTime(String updateTime) { this.updateTime = updateTime; }
}
