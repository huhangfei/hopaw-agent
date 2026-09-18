package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.constant.ProjectLogTypeEnum;
import com.agent.hopaw.infra.mapper.ProjectLogMapper;
import com.agent.hopaw.infra.model.entity.Account;
import com.agent.hopaw.infra.model.entity.ProjectLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ProjectLogService implements IProjectLogService {
    private static final Logger logger = LoggerFactory.getLogger(ProjectLogService.class);

    private final ProjectLogMapper projectLogMapper;
    private final IAccountService accountService;

    public ProjectLogService(ProjectLogMapper projectLogMapper, IAccountService accountService) {
        this.projectLogMapper = projectLogMapper;
        this.accountService = accountService;
    }

    @Override
    public void log(Long projectId, String operatorId, String action, String detail) {
        String operatorName = resolveOperatorName(operatorId);
        log(projectId, operatorId, operatorName, action, detail, ProjectLogTypeEnum.DEFAULT.getCode());
    }

    @Override
    public void log(Long projectId, String operatorId, String operatorName, String action, String detail) {
        log(projectId, operatorId, operatorName, action, detail, ProjectLogTypeEnum.DEFAULT.getCode());
    }

    @Override
    public void log(Long projectId, String operatorId, String operatorName, String action, String detail, String logType) {
        if (projectId == null) {
            return;
        }
        try {
            ProjectLog log = new ProjectLog();
            log.setProjectId(projectId);
            log.setOperatorId(operatorId);
            log.setOperatorName(operatorName != null ? operatorName : "未知");
            log.setAction(action);
            log.setLogType(ProjectLogTypeEnum.fromCode(logType).getCode());
            log.setDetail(detail);
            projectLogMapper.insert(log);
        } catch (Exception e) {
            // 日志写入失败不应影响主业务流程
            logger.error("记录项目操作日志失败: projectId={}, action={}", projectId, action, e);
        }
    }

    @Override
    public List<ProjectLog> getLogsByProjectId(Long projectId) {
        List<ProjectLog> list = projectLogMapper.findByProjectId(projectId);
        return list != null ? list : new ArrayList<>();
    }

    @Override
    public List<ProjectLog> getLogsPage(Long projectId, int page, int size) {
        int pageNo = Math.max(page, 1);
        int pageSize = Math.max(Math.min(size, 100), 1);
        List<ProjectLog> list = projectLogMapper.findPageByProjectId(projectId, (pageNo - 1) * pageSize, pageSize);
        return list != null ? list : new ArrayList<>();
    }

    @Override
    public int countLogs(Long projectId) {
        return projectLogMapper.countByProjectId(projectId);
    }

    @Override
    public List<ProjectLog> getImportantLogsByProjectId(Long projectId) {
        List<ProjectLog> list = projectLogMapper.findByProjectIdAndLogType(projectId, ProjectLogTypeEnum.IMPORTANT.getCode());
        return list != null ? list : new ArrayList<>();
    }

    @Override
    public boolean deleteLog(Long logId) {
        if (logId == null || projectLogMapper.findById(logId) == null) {
            return false;
        }
        return projectLogMapper.deleteById(logId) > 0;
    }

    @Override
    public boolean updateLogType(Long logId, String logType) {
        if (logId == null || projectLogMapper.findById(logId) == null) {
            return false;
        }
        // 未匹配的类型按枚举规则归一为 default
        String normalized = ProjectLogTypeEnum.fromCode(logType).getCode();
        return projectLogMapper.updateLogType(logId, normalized) > 0;
    }

    /** 通过 userId 解析操作者昵称 */
    private String resolveOperatorName(String userId) {
        if (userId == null) {
            return "未知";
        }
        try {
            Account account = accountService.getByUserId(userId);
            if (account != null) {
                return account.getNickname() != null ? account.getNickname() : account.getUsername();
            }
        } catch (Exception e) {
            logger.warn("解析操作者昵称失败: userId={}", userId, e);
        }
        return "未知";
    }
}
