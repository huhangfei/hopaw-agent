package com.agent.hopaw.controller;

import com.agent.hopaw.infra.constant.UserMemoryTypeEnum;
import com.agent.hopaw.infra.memory.ILongTermMemoryService;
import com.agent.hopaw.infra.memory.LongTermMemoryService;
import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.infra.model.entity.LongTermMemory;
import com.agent.hopaw.infra.service.ChatSessionService;
import com.agent.hopaw.util.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Controller
public class MemoryManageController {

    private static final Logger logger = LoggerFactory.getLogger(MemoryManageController.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ILongTermMemoryService longTermMemoryService;

    public MemoryManageController(LongTermMemoryService longTermMemoryService) {
        this.longTermMemoryService = longTermMemoryService;
    }

    @GetMapping("/memory-manage")
    public String page(Model model) {
        return "memory-manage";
    }

    @GetMapping("/api/memory-manage/tree")
    @ResponseBody
    public ResponseBean tree(HttpServletRequest request) {
        List<LongTermMemory> list = longTermMemoryService.queryUserAllMemories(null, CurrentUser.require(request));
        List<Map<String, Object>> types = new ArrayList<>();
        for (UserMemoryTypeEnum typeEnum : UserMemoryTypeEnum.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", typeEnum.getCode());
            item.put("name", typeEnum.getName());
            item.put("count", list.stream().filter(m -> typeEnum.getCode().equals(m.getMemoryType())).count());
            types.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("types", types);
        result.put("memories", list);
        return ResponseBean.success(result);
    }

    @GetMapping("/api/memory-manage/{id}")
    @ResponseBody
    public ResponseBean get(@PathVariable Long id) {
        LongTermMemory memory = longTermMemoryService.getMemoryById(id);
        if (memory == null) {
            return ResponseBean.fail("记忆不存在");
        }
        return ResponseBean.success(memory);
    }

    @PostMapping("/api/memory-manage")
    @ResponseBody
    public ResponseBean create(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        String sessionId = (String) body.get("sessionId");
        String memory = (String) body.get("memory");
        Object parentIdObj = body.get("parentId");
        Long parentId = parentIdObj != null ? Long.valueOf(parentIdObj.toString()) : null;
        String memoryType = (String) body.get("memoryType");
        String summary = (String) body.get("summary");
        LongTermMemory entity = longTermMemoryService.createMemory(sessionId, memory, parentId, CurrentUser.require(request), UserMemoryTypeEnum.fromCode(memoryType), summary);
        return ResponseBean.success(entity);
    }

    @PutMapping("/api/memory-manage/{id}")
    @ResponseBody
    public ResponseBean update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        LongTermMemory entity = longTermMemoryService.getMemoryById(id);
        if (entity == null) {
            return ResponseBean.fail("记忆不存在");
        }
        if (body.containsKey("memory")) {
            entity.setMemory(body.get("memory"));
            entity.setMemoryHash(String.valueOf(body.get("memory").hashCode()));
        }
        if (body.containsKey("summary")) {
            entity.setSummary(body.get("summary"));
        }
        if (body.containsKey("memoryType")) {
            String newType = body.get("memoryType");
            entity.setMemoryType(newType);
        }
        longTermMemoryService.update(entity);
        return ResponseBean.success(null);
    }

    @PutMapping("/api/memory-manage/{id}/move")
    @ResponseBody
    public ResponseBean move(@PathVariable Long id, @RequestParam(required = false) Long newParentId) {
        if (newParentId != null) {
            LongTermMemory target = longTermMemoryService.getMemoryById(newParentId);
            LongTermMemory source = longTermMemoryService.getMemoryById(id);
            if (target == null || source == null) {
                return ResponseBean.fail("记忆不存在");
            }
            if (source.getMemoryType() != null && !source.getMemoryType().equals(target.getMemoryType())) {
                return ResponseBean.fail("不能移动到不同记忆类型下");
            }
        }
        longTermMemoryService.moveMemory(id, newParentId);
        return ResponseBean.success(null);
    }

    @DeleteMapping("/api/memory-manage/{id}")
    @ResponseBody
    public ResponseBean delete(@PathVariable Long id) {
        longTermMemoryService.deleteMemory(id);
        return ResponseBean.success(null);
    }

    @DeleteMapping("/api/memory-manage/clear-all")
    @ResponseBody
    public ResponseBean clearAll(HttpServletRequest request) {
        String userId = CurrentUser.require(request);
        longTermMemoryService.deleteAllMemoriesByUserId(userId);
        return ResponseBean.success(null);
    }

    @GetMapping("/api/memory-manage/export")
    public void export(HttpServletRequest request, HttpServletResponse response) {
        String userId = CurrentUser.require(request);
        List<LongTermMemory> memories = longTermMemoryService.queryUserAllMemories(null, userId);

        response.setContentType("application/zip");
        String fileName = "memories_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".zip";
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");

        try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream(), StandardCharsets.UTF_8)) {
            Set<String> usedNames = new HashSet<>();
            for (LongTermMemory m : memories) {
                String safeName = sanitizeFileName(m.getSummary() != null ? m.getSummary() : "memory_" + m.getId());
                // 避免文件名冲突
                String uniqueName = safeName;
                int idx = 1;
                while (usedNames.contains(uniqueName + ".md")) {
                    uniqueName = safeName + "_" + idx++;
                }
                usedNames.add(uniqueName + ".md");

                StringBuilder content = new StringBuilder();
                content.append("---\n");
                UserMemoryTypeEnum typeEnum = UserMemoryTypeEnum.fromCode(m.getMemoryType());
                content.append("类型: ").append(typeEnum != null ? typeEnum.getName() : m.getMemoryType()).append("\n");
                content.append("类型编码: ").append(m.getMemoryType()).append("\n");
                content.append("概述: ").append(m.getSummary() != null ? m.getSummary() : "").append("\n");
                content.append("时间: ").append(m.getCreateTime() != null ? m.getCreateTime().format(FORMATTER) : "").append("\n");
                content.append("---\n\n");
                content.append(m.getMemory() != null ? m.getMemory() : "");

                ZipEntry entry = new ZipEntry(uniqueName + ".md");
                zos.putNextEntry(entry);
                zos.write(content.toString().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            zos.finish();
        } catch (Exception e) {
            logger.error("Failed to export memories, userId={}", userId, e);
        }
    }

    @PostMapping("/api/memory-manage/import")
    @ResponseBody
    public ResponseBean importMemories(HttpServletRequest request, @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseBean.fail("请选择文件");
        }
        String userId = CurrentUser.require(request);
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase().endsWith(".zip")) {
            return ResponseBean.fail("请上传zip格式文件");
        }

        int successCount = 0;
        int failCount = 0;

        try (ZipInputStream zis = new ZipInputStream(file.getInputStream(), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String entryName = entry.getName();
                if (!entryName.toLowerCase().endsWith(".md")) {
                    continue;
                }

                try {
                    String content = readStreamToString(zis);
                    ParsedMemory parsed = parseMarkdown(content);
                    if (parsed == null || parsed.memoryType == null) {
                        failCount++;
                        continue;
                    }
                    UserMemoryTypeEnum typeEnum = UserMemoryTypeEnum.fromCode(parsed.memoryType);
                    if (typeEnum == null) {
                        failCount++;
                        continue;
                    }
                    longTermMemoryService.saveUserMemory(null, userId, typeEnum, parsed.summary, parsed.memory);
                    successCount++;
                } catch (Exception e) {
                    logger.warn("Failed to parse memory file: {}", entryName, e);
                    failCount++;
                }
            }
        } catch (Exception e) {
            logger.error("Failed to import memories", e);
            return ResponseBean.fail("导入失败: " + e.getMessage());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("success", successCount);
        data.put("fail", failCount);
        return ResponseBean.success(data);
    }

    private String sanitizeFileName(String name) {
        if (name == null || name.isBlank()) {
            return "untitled";
        }
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private String readStreamToString(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int len;
        while ((len = is.read(buffer)) != -1) {
            baos.write(buffer, 0, len);
        }
        return baos.toString(StandardCharsets.UTF_8.name());
    }

    private static class ParsedMemory {
        String memoryType;
        String summary;
        String memory;
    }

    private ParsedMemory parseMarkdown(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }

        ParsedMemory result = new ParsedMemory();
        result.memory = "";

        // 解析 front matter（--- 包围的头部）
        int firstSep = content.indexOf("---\n");
        if (firstSep != 0) {
            // 没有 front matter，整体当作内容
            result.memory = content.trim();
            return result;
        }

        int secondSep = content.indexOf("\n---", firstSep + 4);
        if (secondSep == -1) {
            result.memory = content.trim();
            return result;
        }

        String header = content.substring(firstSep + 4, secondSep);
        result.memory = content.substring(secondSep + 4).trim();

        for (String line : header.split("\n")) {
            int colon = line.indexOf(":");
            if (colon == -1) continue;
            String key = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            if (key.equals("类型编码")) {
                result.memoryType = value;
            } else if (key.equals("概述")) {
                result.summary = value;
            }
        }

        return result;
    }
}
