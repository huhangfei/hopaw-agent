package com.agent.hopaw.controller;

import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.infra.model.entity.Attachment;
import com.agent.hopaw.infra.service.IAttachmentService;
import com.agent.hopaw.util.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Controller
public class AttachmentController {

    private static final Logger logger = LoggerFactory.getLogger(AttachmentController.class);

    private final IAttachmentService attachmentService;

    @Value("${hopaw.attachment.dir:./attachments}")
    private String attachmentDir;

    @Value("${hopaw.attachment.url-prefix:/attachments}")
    private String urlPrefix;

    private String attachmentRoot;

    public AttachmentController(IAttachmentService attachmentService) {
        this.attachmentService = attachmentService;
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

    @GetMapping("/attachments")
    public String index(Model model) {
        model.addAttribute("activePage", "attachments");
        return "attachments";
    }

    /**
     * 独立附件预览页：按附件ID路由到预览页，由前端按 fileType 渲染。
     * 用于在新标签页中打开预览，避免模态框重复代码。
     */
    @GetMapping("/attachment-preview/{id}")
    public String previewPage(@PathVariable Long id, HttpServletRequest request, Model model) {
        String userId = CurrentUser.require(request);
        Attachment attachment = attachmentService.getAttachment(id, userId);
        if (attachment == null) {
            model.addAttribute("error", "附件不存在或无权访问");
        } else {
            model.addAttribute("attachment", attachment);
        }
        model.addAttribute("activePage", "");
        return "attachment-preview";
    }

    @PostMapping("/api/attachments/upload")
    @ResponseBody
    public ResponseBean upload(HttpServletRequest request,
                               @RequestParam("files") MultipartFile[] files,
                               @RequestParam(required = false, defaultValue = "upload") String source,
                               @RequestParam(required = false) Long bizId) {
        String userId = CurrentUser.require(request);
        if (files == null || files.length == 0) {
            return ResponseBean.fail("请选择文件");
        }
        try {
            List<Attachment> result = new ArrayList<>();
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                Attachment attachment = doUpload(userId, file, source, bizId);
                result.add(attachment);
            }
            return ResponseBean.success(result);
        } catch (Exception e) {
            logger.error("批量上传附件失败", e);
            return ResponseBean.fail(e.getMessage());
        }
    }

    private Attachment doUpload(String userId, MultipartFile file, String source, Long bizId) throws IOException {
        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf("."));
        }
        String extLower = ext.toLowerCase(Locale.ROOT);

        String dateDir = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        Path dirPath = Paths.get(attachmentRoot, dateDir);
        Files.createDirectories(dirPath);

        String newName = UUID.randomUUID().toString().replace("-", "") + extLower;
        Path filePath = dirPath.resolve(newName);
        file.transferTo(filePath.toFile());

        String storagePath = dateDir + "/" + newName;
        String url = urlPrefix + "/" + storagePath;
        String fileType = getFileType(extLower);
        String mimeType = file.getContentType();

        Attachment attachment = new Attachment();
        attachment.setOriginalName(originalName);
        attachment.setStorageName(newName);
        attachment.setUrl(url);
        attachment.setFileType(fileType);
        attachment.setFileExtension(extLower);
        attachment.setFileSize(file.getSize());
        attachment.setMimeType(mimeType);
        attachment.setSource(source != null ? source : "upload");
        attachment.setBizId(bizId);
        attachment.setUserId(userId);
        attachment.setStoragePath(storagePath);

        return attachmentService.createAttachment(attachment);
    }

    @GetMapping("/api/attachments/page")
    @ResponseBody
    public ResponseBean getAttachmentsPage(HttpServletRequest request,
                                           @RequestParam(required = false, defaultValue = "") String keyword,
                                           @RequestParam(required = false, defaultValue = "") String source,
                                           @RequestParam(required = false, defaultValue = "") String tag,
                                           @RequestParam(required = false, defaultValue = "") String fileType,
                                           @RequestParam(required = false, defaultValue = "1") int page,
                                           @RequestParam(required = false, defaultValue = "12") int size) {
        String userId = CurrentUser.require(request);
        List<Attachment> list = attachmentService.getAttachmentsPage(userId, keyword, source, tag, fileType, page, size);
        int total = attachmentService.countAttachments(userId, keyword, source, tag, fileType);
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return ResponseBean.success(result);
    }

    @GetMapping("/api/attachments/{id}")
    @ResponseBody
    public ResponseBean getAttachment(HttpServletRequest request, @PathVariable Long id) {
        String userId = CurrentUser.require(request);
        Attachment attachment = attachmentService.getAttachment(id, userId);
        if (attachment == null) {
            return ResponseBean.fail("附件不存在");
        }
        return ResponseBean.success(attachment);
    }

    @PutMapping("/api/attachments/{id}")
    @ResponseBody
    public ResponseBean updateAttachment(HttpServletRequest request,
                                         @PathVariable Long id,
                                         @RequestBody Attachment attachment) {
        String userId = CurrentUser.require(request);
        attachment.setId(id);
        try {
            Attachment updated = attachmentService.updateAttachment(attachment, userId);
            return ResponseBean.success(updated);
        } catch (Exception e) {
            return ResponseBean.fail(e.getMessage());
        }
    }

    @DeleteMapping("/api/attachments/{id}")
    @ResponseBody
    public ResponseBean deleteAttachment(HttpServletRequest request, @PathVariable Long id) {
        String userId = CurrentUser.require(request);
        try {
            // 先查出附件信息，用于删除物理文件
            Attachment attachment = attachmentService.getAttachment(id, userId);
            if (attachment == null) {
                return ResponseBean.fail("附件不存在");
            }
            attachmentService.deleteAttachment(id, userId);
            // 删除物理文件（仅当没有其他记录引用同一文件时）
            if (attachment.getStoragePath() != null && attachmentService.countByStoragePath(attachment.getStoragePath()) <= 0) {
                try {
                    Path filePath = Paths.get(attachmentRoot, attachment.getStoragePath());
                    Files.deleteIfExists(filePath);
                    logger.info("已删除附件文件: {}", filePath);
                } catch (IOException e) {
                    logger.warn("删除附件文件失败: {}", e.getMessage());
                }
            }
            return ResponseBean.success();
        } catch (Exception e) {
            return ResponseBean.fail(e.getMessage());
        }
    }

    /**
     * 根据扩展名判断文件类型
     */
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
