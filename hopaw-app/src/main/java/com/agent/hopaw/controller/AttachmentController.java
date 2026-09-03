package com.agent.hopaw.controller;

import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.infra.model.entity.Attachment;
import com.agent.hopaw.infra.service.IAttachmentService;
import com.agent.hopaw.util.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class AttachmentController {

    private static final Logger logger = LoggerFactory.getLogger(AttachmentController.class);

    private final IAttachmentService attachmentService;

    public AttachmentController(IAttachmentService attachmentService) {
        this.attachmentService = attachmentService;
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

    /**
     * 公共文件预览页：仅负责文件内容展示和下载，不绑定任何业务逻辑。
     * 接收 URL 参数 url（文件内容 URL）和 name（文件名），由前端按扩展名路由渲染。
     * 供 iframe 嵌套使用（附件预览、项目空间文件预览等场景复用）。
     */
    @GetMapping("/file-preview")
    public String filePreviewPage() {
        return "file-preview";
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
                // 文件落盘、类型识别与记录创建在 service 层执行（契约层不引入 Spring Web 依赖）
                Attachment attachment = attachmentService.uploadAttachment(
                        userId,
                        file.getOriginalFilename(),
                        file.getContentType(),
                        file.getBytes(),
                        source,
                        bizId);
                result.add(attachment);
            }
            return ResponseBean.success(result);
        } catch (Exception e) {
            logger.error("批量上传附件失败", e);
            return ResponseBean.fail(e.getMessage());
        }
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
            // 存在性校验与物理文件清理由 service.deleteAttachment 处理
            attachmentService.deleteAttachment(id, userId);
            return ResponseBean.success();
        } catch (Exception e) {
            return ResponseBean.fail(e.getMessage());
        }
    }
}