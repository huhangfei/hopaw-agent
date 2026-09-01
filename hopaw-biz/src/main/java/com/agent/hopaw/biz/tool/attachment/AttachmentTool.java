package com.agent.hopaw.biz.tool.attachment;

import com.agent.hopaw.infra.model.entity.Attachment;
import com.agent.hopaw.infra.service.IAttachmentService;
import com.agent.hopaw.infra.tool.ToolSecurityLevel;
import com.agent.hopaw.infra.tool.AgentTool;
import com.agent.hopaw.infra.util.InvocationParametersWrapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

/**
 * 附件工具集：将指定文件添加到附件系统，返回附件的预览地址和下载地址。
 *
 * <p>存储逻辑与 {@code AttachmentController.doUpload} 保持一致：
 * 文件按日期分目录保存，URL 由 {@code hopaw.attachment.url-prefix} 拼接，
 * 附件归属当前调用用户（通过 InvocationParameters 获取 userId），保证权限隔离。
 */
@Component("attachmentTool")
public class AttachmentTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(AttachmentTool.class);

    /** 附件来源标识：工具添加 */
    private static final String SOURCE = "agentTool";

    private final IAttachmentService attachmentService;

    @Value("${hopaw.attachment.dir:./attachments}")
    private String attachmentDir;

    @Value("${hopaw.attachment.url-prefix:/attachments}")
    private String urlPrefix;

    public AttachmentTool(IAttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @Override
    public String getName() {
        return "attachmentTool";
    }

    @Override
    public String getDescription() {
        return "附件工具集，支持将指定文件添加到附件系统并返回预览地址与下载地址";
    }

    @Override
    public String getKeyword() {
        return "附件";
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.PARAM_REQUIRE_APPROVAL)
    @Tool(value = {"添加附件", "将指定路径的文件复制到附件系统，返回该附件的预览地址和下载地址", "文件上传为附件"})
    public String addFileToAttachment(
            @P(description = "要添加为附件的文件路径") String filePath,
            @P(description = "附件备注（可选）", required = false) String remark,
            @P(description = "附件标签，多个用逗号分隔（可选）", required = false) String tags,
            InvocationParameters invocationParameters) {
        try {
            Path sourcePath = Paths.get(filePath).toAbsolutePath().normalize();
            if (!Files.exists(sourcePath)) {
                return "错误: 文件不存在: " + filePath;
            }
            if (!Files.isRegularFile(sourcePath)) {
                return "错误: 路径不是文件: " + filePath;
            }

            String userId = InvocationParametersWrapper.create(invocationParameters).getUserId();
            if (userId == null || userId.isEmpty()) {
                return "错误: 无法获取当前用户，拒绝添加附件";
            }

            // 复制文件到附件存储目录（与附件上传逻辑一致：日期分目录、UUID命名）
            String originalName = sourcePath.getFileName().toString();
            String ext = "";
            if (originalName.contains(".")) {
                ext = originalName.substring(originalName.lastIndexOf(".")).toLowerCase(Locale.ROOT);
            }
            String dateDir = LocalDate.now().toString();
            Path dirPath = Paths.get(attachmentRoot(), dateDir);
            Files.createDirectories(dirPath);
            String newName = UUID.randomUUID().toString().replace("-", "") + ext;
            Path targetPath = dirPath.resolve(newName);
            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);

            String storagePath = dateDir + "/" + newName;
            String url = urlPrefix + "/" + storagePath;
            long fileSize = Files.size(sourcePath);

            Attachment attachment = new Attachment();
            attachment.setOriginalName(originalName);
            attachment.setStorageName(newName);
            attachment.setUrl(url);
            attachment.setFileType(getFileType(ext));
            attachment.setFileExtension(ext);
            attachment.setFileSize(fileSize);
            // Windows 上 probeContentType 依赖注册表可能返回 null，回退通用二进制类型
            String mimeType = Files.probeContentType(sourcePath);
            attachment.setMimeType(mimeType != null ? mimeType : "application/octet-stream");
            attachment.setSource(SOURCE);
            attachment.setRemark(remark);
            attachment.setTags(tags);
            attachment.setUserId(userId);
            attachment.setStoragePath(storagePath);

            Attachment saved = attachmentService.createAttachment(attachment);
            String previewUrl = "/attachment-preview/" + saved.getId();

            StringBuilder sb = new StringBuilder();
            sb.append("附件添加成功\n");
            sb.append("附件ID: ").append(saved.getId()).append("\n");
            sb.append("文件名: ").append(originalName).append("\n");
            sb.append("类型: ").append(attachment.getFileType()).append(ext).append("\n");
            sb.append("大小: ").append(attachment.getFileSize()).append(" 字节\n");
            sb.append("下载地址: ").append(url).append("\n");
            sb.append("预览地址: ").append(previewUrl).append("\n");
            sb.append("说明: 预览地址返回HTML预览页，下载地址为原始文件资源");
            return sb.toString();
        } catch (IOException e) {
            log.error("添加附件失败: {}", filePath, e);
            return "错误: 添加附件失败 - " + e.getMessage();
        }
    }

    /** 附件根目录：相对路径基于应用工作目录解析，与 AttachmentController 一致 */
    private String attachmentRoot() {
        File dir = new File(attachmentDir);
        if (!dir.isAbsolute()) {
            dir = new File(System.getProperty("user.dir"), attachmentDir);
        }
        return dir.getAbsolutePath();
    }

    /** 根据扩展名判断文件类型，与附件上传逻辑保持一致 */
    private String getFileType(String ext) {
        if (ext == null || ext.isEmpty()) {
            return "file";
        }
        switch (ext) {
            case ".png":
            case ".jpg":
            case ".jpeg":
            case ".gif":
            case ".bmp":
            case ".webp":
            case ".svg":
                return "image";
            case ".mp4":
            case ".webm":
            case ".ogg":
            case ".mov":
            case ".avi":
            case ".mkv":
                return "video";
            case ".mp3":
            case ".wav":
            case ".flac":
            case ".aac":
            case ".m4a":
                return "audio";
            case ".pdf":
                return "pdf";
            case ".md":
            case ".markdown":
                return "markdown";
            case ".txt":
            case ".log":
            case ".csv":
            case ".json":
            case ".xml":
            case ".yml":
            case ".yaml":
            case ".html":
            case ".css":
            case ".js":
            case ".java":
            case ".py":
            case ".sql":
            case ".sh":
            case ".bat":
            case ".properties":
            case ".ini":
            case ".conf":
                return "text";
            default:
                return "file";
        }
    }
}