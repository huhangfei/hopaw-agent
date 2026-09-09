package com.agent.hopaw.infra.model.entity;

/**
 * IP黑名单
 */
public class IpBlacklist {

    private Long id;
    /** IP地址 */
    private String ip;
    /** 备注 */
    private String remark;
    private String createTime;

    public IpBlacklist() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }
}
