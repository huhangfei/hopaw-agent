package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.constant.AttachmentSourceEnum;
import com.agent.hopaw.infra.mapper.AttachmentMapper;
import com.agent.hopaw.infra.model.entity.Attachment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class AttachmentService implements IAttachmentService {

    private static final Logger logger = LoggerFactory.getLogger(AttachmentService.class);

    /** 支持压缩的图片扩展名（gif 动图、webp/svg 暂不支持） */
    private static final Set<String> COMPRESSIBLE_IMAGE_EXTS = new HashSet<>(Arrays.asList(".jpg", ".jpeg", ".png", ".bmp"));

    private final AttachmentMapper attachmentMapper;
    private final ImageUploadConfigService imageUploadConfigService;

    @Value("${hopaw.attachment.dir:./attachments}")
    private String attachmentDir;

    @Value("${hopaw.attachment.url-prefix:/attachments}")
    private String urlPrefix;

    private String attachmentRoot;

    public AttachmentService(AttachmentMapper attachmentMapper, ImageUploadConfigService imageUploadConfigService) {
        this.attachmentMapper = attachmentMapper;
        this.imageUploadConfigService = imageUploadConfigService;
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
            deletePhysicalFile(attachment.getStoragePath());
        }
        // 源文件与处理后文件分离时（图片压缩场景），单独清理源文件
        String sourcePath = attachment.getSourceStoragePath();
        if (sourcePath != null && !sourcePath.equals(attachment.getStoragePath())
                && attachmentMapper.countBySourceStoragePath(sourcePath) <= 0
                && attachmentMapper.countByStoragePath(sourcePath) <= 0) {
            deletePhysicalFile(sourcePath);
        }
    }

    /** 删除附件目录下的物理文件（文件不存在时忽略） */
    private void deletePhysicalFile(String storagePath) {
        try {
            Path filePath = Paths.get(attachmentRoot, storagePath);
            Files.deleteIfExists(filePath);
            logger.info("已删除附件文件: {}", filePath);
        } catch (IOException e) {
            logger.warn("删除附件文件失败: {}", e.getMessage());
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
        String storagePath;
        String sourceStoragePath;
        byte[] finalContent;
        try {
            dateDir = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            Path dirPath = Paths.get(attachmentRoot, dateDir);
            Files.createDirectories(dirPath);

            // 图片类型且配置开启时按分档压缩：原图存入源文件地址，压缩图作为附件文件
            byte[] compressed = tryCompressImage(extLower, content);
            if (compressed != null) {
                String sourceName = UUID.randomUUID().toString().replace("-", "") + "_source" + extLower;
                Files.write(dirPath.resolve(sourceName), content);
                newName = UUID.randomUUID().toString().replace("-", "") + ".jpg";
                Files.write(dirPath.resolve(newName), compressed);
                sourceStoragePath = dateDir + "/" + sourceName;
                finalContent = compressed;
                logger.info("图片已压缩: {} {}KB -> {}KB", originalName, content.length / 1024, compressed.length / 1024);
            } else {
                newName = UUID.randomUUID().toString().replace("-", "") + extLower;
                Files.write(dirPath.resolve(newName), content);
                sourceStoragePath = null;
                finalContent = content;
            }
            storagePath = dateDir + "/" + newName;
        } catch (IOException e) {
            logger.error("附件文件写入失败: {}", originalName, e);
            throw new RuntimeException("附件文件写入失败: " + e.getMessage());
        }

        // 源文件地址：未压缩时与处理后的文件地址一致
        String sourcePathValue = (sourceStoragePath != null) ? sourceStoragePath : storagePath;
        String url = urlPrefix + "/" + storagePath;
        String sourceUrl = urlPrefix + "/" + sourcePathValue;
        String fileType = getFileType(extLower);

        Attachment attachment = new Attachment();
        attachment.setOriginalName(originalName);
        attachment.setStorageName(newName);
        attachment.setUrl(url);
        attachment.setFileType(fileType);
        attachment.setFileExtension(extLower);
        attachment.setFileSize((long) finalContent.length);
        attachment.setMimeType(contentType);
        attachment.setSource(source != null ? source : AttachmentSourceEnum.UPLOAD.getCode());
        attachment.setBizId(bizId);
        attachment.setUserId(userId);
        attachment.setStoragePath(storagePath);
        attachment.setSourceUrl(sourceUrl);
        attachment.setSourceStoragePath(sourcePathValue);

        return createAttachment(attachment);
    }

    /**
     * 按配置尝试压缩图片：图片类型 + 压缩开关开启 + 文件大小命中分档（minSize 决定压缩质量）时压缩为 JPEG。
     *
     * @return 压缩后的字节数组；不适用压缩、压缩失败或压缩后反而更大时返回 null（回退存原图）
     */
    private byte[] tryCompressImage(String extLower, byte[] content) {
        if (!COMPRESSIBLE_IMAGE_EXTS.contains(extLower) || !imageUploadConfigService.isCompressEnabled()) {
            return null;
        }
        Float quality = imageUploadConfigService.matchQuality(content.length);
        if (quality == null) {
            // 文件小于所有分档的最小大小，不压缩
            return null;
        }
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(content));
            if (src == null || src.getWidth() <= 0 || src.getHeight() <= 0) {
                return null;
            }
            // 带 Alpha 通道的图片（如 PNG 截图）转 JPEG 前填充白色背景
            BufferedImage img = src;
            if (src.getColorModel().hasAlpha()) {
                BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = rgb.createGraphics();
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, src.getWidth(), src.getHeight());
                g.drawImage(src, 0, 0, null);
                g.dispose();
                img = rgb;
            }
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(bos)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(img, null, null), param);
            } finally {
                writer.dispose();
            }
            byte[] result = bos.toByteArray();
            // 压缩后不小于原图时无收益，回退原图
            return result.length < content.length ? result : null;
        } catch (Exception e) {
            logger.warn("图片压缩失败，回退原图: {}", e.getMessage());
            return null;
        }
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
