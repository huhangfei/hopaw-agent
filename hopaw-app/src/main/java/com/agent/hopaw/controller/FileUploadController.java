package com.agent.hopaw.controller;

import com.agent.hopaw.infra.model.dto.ResponseBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 文件上传控制器
 */
@RestController
@RequestMapping("/api")
public class FileUploadController {

    private static final Logger logger = LoggerFactory.getLogger(FileUploadController.class);

    @Value("${hopaw.upload.dir:./uploads}")
    private String uploadDir;

    /** 解析后的绝对路径 */
    private String uploadRoot;

    @PostConstruct
    public void init() {
        // 将相对路径解析为基于 user.dir 的绝对路径，避免 Tomcat 临时目录问题
        File dir = new File(uploadDir);
        if (!dir.isAbsolute()) {
            dir = new File(System.getProperty("user.dir"), uploadDir);
        }
        this.uploadRoot = dir.getAbsolutePath();
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    @PostMapping("/upload")
    public ResponseBean upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseBean.fail("文件为空");
        }
        try {
            // 按日期分目录
            String dateDir = String.format("%tF", new Date());
            Path dirPath = Paths.get(uploadRoot, dateDir);
            Files.createDirectories(dirPath);

            // 生成唯一文件名
            String originalName = file.getOriginalFilename();
            String ext = "";
            if (originalName != null && originalName.contains(".")) {
                ext = originalName.substring(originalName.lastIndexOf("."));
            }
            String newName = UUID.randomUUID().toString().replace("-", "") + ext;

            Path filePath = dirPath.resolve(newName);
            file.transferTo(filePath.toFile());

            // 返回访问 URL
            String url = "/uploads/" + dateDir + "/" + newName;
            String type = getFileType(ext);

            Map<String, String> result = new LinkedHashMap<>();
            result.put("url", url);
            result.put("type", type);
            result.put("name", originalName != null ? originalName : newName);
            return ResponseBean.success(result);
        } catch (IOException e) {
            logger.error("文件上传失败", e);
            return ResponseBean.fail("上传失败: " + e.getMessage());
        }
    }

    private String getFileType(String ext) {
        if (ext == null) return "file";
        String lower = ext.toLowerCase();
        switch (lower) {
            case ".png":
            case ".jpg":
            case ".jpeg":
            case ".gif":
            case ".bmp":
            case ".webp":
                return "image";
            case ".pdf":
                return "pdf";
            default:
                return "file";
        }
    }
}