package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.model.entity.ProjectLog;

import java.util.List;

/**
 * 项目操作日志服务接口
 */
public interface IProjectLogService {

    /**
     * 记录项目操作日志
     *
     * @param projectId     项目ID
     * @param operatorId    操作者用户ID
     * @param action        动作类型
     * @param detail        操作内容描述
     */
    void log(Long projectId, String operatorId, String action, String detail);

    /**
     * 记录项目操作日志（带操作者昵称）
     */
    void log(Long projectId, String operatorId, String operatorName, String action, String detail);

    /**
     * 记录项目操作日志（带日志类型）
     * @param logType 日志类型，见 ProjectLogTypeEnum：default=默认 / important=重点
     */
    void log(Long projectId, String operatorId, String operatorName, String action, String detail, String logType);

    /** 查询项目操作日志，按时间正序 */
    List<ProjectLog> getLogsByProjectId(Long projectId);

    /** 查询项目重点日志（important 类型），按时间正序 */
    List<ProjectLog> getImportantLogsByProjectId(Long projectId);
}
