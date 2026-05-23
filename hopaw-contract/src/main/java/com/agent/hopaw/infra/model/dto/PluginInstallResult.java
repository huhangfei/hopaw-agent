package com.agent.hopaw.infra.model.dto;

/**
 * 插件安装/更新结果 DTO
 */
public class PluginInstallResult {
    private String toolName;
    private String version;
    private String fileName;
    private boolean success;
    private String message;
    private int toolCount;
    private boolean isUpgrade;
    private String previousVersion;
    private PluginConflictInfo conflictInfo;

    public PluginInstallResult() {
    }

    public PluginInstallResult(String toolName, String version, String fileName) {
        this.toolName = toolName;
        this.version = version;
        this.fileName = fileName;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getToolCount() {
        return toolCount;
    }

    public void setToolCount(int toolCount) {
        this.toolCount = toolCount;
    }

    public boolean isUpgrade() {
        return isUpgrade;
    }

    public void setUpgrade(boolean upgrade) {
        isUpgrade = upgrade;
    }

    public String getPreviousVersion() {
        return previousVersion;
    }

    public void setPreviousVersion(String previousVersion) {
        this.previousVersion = previousVersion;
    }

    public PluginConflictInfo getConflictInfo() {
        return conflictInfo;
    }

    public void setConflictInfo(PluginConflictInfo conflictInfo) {
        this.conflictInfo = conflictInfo;
    }

    public static PluginInstallResult success(String toolName, String version, String fileName, 
                                               int toolCount, boolean isUpgrade, String previousVersion) {
        PluginInstallResult result = new PluginInstallResult(toolName, version, fileName);
        result.setSuccess(true);
        result.setToolCount(toolCount);
        result.setUpgrade(isUpgrade);
        result.setPreviousVersion(previousVersion);
        
        if (isUpgrade) {
            result.setMessage(String.format("成功更新插件 %s 从 v%s 到 v%s，加载了 %d 个工具", 
                                          toolName, previousVersion, version, toolCount));
        } else {
            result.setMessage(String.format("成功安装插件 %s v%s，加载了 %d 个工具", 
                                          toolName, version, toolCount));
        }
        return result;
    }

    public static PluginInstallResult success(String toolName, String version, String fileName, 
                                               int toolCount, boolean isUpgrade, String previousVersion,
                                               PluginConflictInfo conflictInfo) {
        PluginInstallResult result = success(toolName, version, fileName, toolCount, isUpgrade, previousVersion);
        result.setConflictInfo(conflictInfo);
        if (conflictInfo != null && conflictInfo.hasConflicts()) {
            result.setMessage(result.getMessage() + "\n⚠️ 警告: " + conflictInfo.getMessage());
        }
        return result;
    }

    public static PluginInstallResult fail(String toolName, String version, String fileName, String message) {
        PluginInstallResult result = new PluginInstallResult(toolName, version, fileName);
        result.setSuccess(false);
        result.setMessage(message);
        return result;
    }
}
