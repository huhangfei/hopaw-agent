package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.mapper.AttachmentMapper;
import com.agent.hopaw.infra.model.entity.Attachment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AttachmentService implements IAttachmentService {

    private static final Logger logger = LoggerFactory.getLogger(AttachmentService.class);

    private final AttachmentMapper attachmentMapper;

    @Value("${hopaw.attachment.dir:./attachments}")
    private String attachmentDir;

    @Value("${hopaw.attachment.url-prefix:/attachments}")
    private String urlPrefix;

    private String attachmentRoot;

    public AttachmentService(AttachmentMapper attachmentMapper) {
        this.attachmentMapper = attachmentMapper;
    }


    @PostConstruct
    public void init() {
        File dir = new File(attachmentDir);
        if (!dir.isAbsolute()) {
            dir = new File(System.getProperty("user.dir"), attachmentDir);
        }
        this.attachmentRoot = dir.getAbsolutePath();
        if (!dir.exists()) {
            dir.mkdirs();
        }
        logger.info("附件存储目录: {}", attachmentRoot);
    }

    @Override
    public Attachment createAttachment(Attachment attachment) {
        attachmentMapper.insert(attachment);
        logger.info("附件记录创建成功: {} -> {}", attachment.getOriginalName(), attachment.getUrl());
        return attachment;
    }

    @Override
    public void deleteAttachment(Long id, String userId) {
        Attachment attachment = attachmentMapper.findById(id);
        if (attachment == null) {
            throw new RuntimeException("附件不存在");
        }
        if (!userId.equals(attachment.getUserId())) {
            throw new RuntimeException("无权删除该附件");
        }
        attachmentMapper.deleteById(id);
        // 删除物理文件（仅当没有其他记录引用同一文件时）
        if (attachment.getStoragePath() != null && countByStoragePath(attachment.getStoragePath()) <= 0) {
            try {
                Path filePath = Paths.get(attachmentRoot, attachment.getStoragePath());
                Files.deleteIfExists(filePath);
                logger.info("已删除附件文件: {}", filePath);
            } catch (IOException e) {
                logger.warn("删除附件文件失败: {}", e.getMessage());
            }
        }
    }

    @Override
    public Attachment updateAttachment(Attachment attachment, String userId) {
        Attachment existing = attachmentMapper.findById(attachment.getId());
        if (existing == null) {
            throw new RuntimeException("附件不存在");
        }
        if (!userId.equals(existing.getUserId())) {
            throw new RuntimeException("无权修改该附件");
        }
        existing.setTags(attachment.getTags());
        existing.setRemark(attachment.getRemark());
        if (attachment.getSource() != null) {
            existing.setSource(attachment.getSource());
        }
        if (attachment.getBizId() != null) {
            existing.setBizId(attachment.getBizId());
        }
        attachmentMapper.update(existing);
        return existing;
    }

    @Override
    public Attachment getAttachment(Long id, String userId) {
        Attachment attachment = attachmentMapper.findById(id);
        if (attachment == null) {
            return null;
        }
        if (!userId.equals(attachment.getUserId())) {
            return null;
        }
        return attachment;
    }

    @Override
    public List<Attachment> getAttachmentsPage(String userId, String keyword, String source, String tag, String fileType, int page, int size) {
        int offset = (page - 1) * size;
        return attachmentMapper.findByUserIdWithFilters(userId, keyword, source, tag, fileType, offset, size);
    }

    @Override
    public int countAttachments(String userId, String keyword, String source, String tag, String fileType) {
        return attachmentMapper.countByUserIdWithFilters(userId, keyword, source, tag, fileType);
    }

    @Override
    public int countByStoragePath(String storagePath) {
        return attachmentMapper.countByStoragePath(storagePath);
    }

    @Override
    public Path getAbsolutePath(Long id) {
        Attachment attachment = attachmentMapper.findById(id);
        if(attachment == null){
            return null;
        }
        Path dirPath = Paths.get(attachmentRoot, attachment.getStoragePath());
        return dirPath;
    }

    @Override
    public Attachment uploadAttachment(String userId, String originalName, String contentType, byte[] content, String source, Long bizId) {
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf("."));
        }
        String extLower = ext.toLowerCase(Locale.ROOT);

        String dateDir;
        String newName;
        try {
            dateDir = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            Path dirPath = Paths.get(attachmentRoot, dateDir);
            Files.createDirectories(dirPath);

            newName = UUID.randomUUID().toString().replace("-", "") + extLower;
            Path filePath = dirPath.resolve(newName);
            Files.write(filePath, content);
        } catch (IOException e) {
            logger.error("附件文件写入失败: {}", originalName, e);
            throw new RuntimeException("附件文件写入失败: " + e.getMessage());
        }

        String storagePath = dateDir + "/" + newName;
        String url = urlPrefix + "/" + storagePath;
        String fileType = getFileType(extLower);

        Attachment attachment = new Attachment();
        attachment.setOriginalName(originalName);
        attachment.setStorageName(newName);
        attachment.setUrl(url);
        attachment.setFileType(fileType);
        attachment.setFileExtension(extLower);
        attachment.setFileSize((long) content.length);
        attachment.setMimeType(contentType);
        attachment.setSource(source != null ? source : "upload");
        attachment.setBizId(bizId);
        attachment.setUserId(userId);
        attachment.setStoragePath(storagePath);

        return createAttachment(attachment);
    }

    /**
     * 根据扩展名判断文件类型
     */
    private String getFileType(String ext) {
        if (ext == null || ext.isEmpty()) {
            return "file";
        }
        switch (ext.toLowerCase(Locale.ROOT)) {
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
