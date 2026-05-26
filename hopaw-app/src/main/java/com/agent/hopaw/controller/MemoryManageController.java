package com.agent.hopaw.controller;

import com.agent.hopaw.constant.DefaultUser;
import com.agent.hopaw.infra.constant.LongTermMemoryTypeEnum;
import com.agent.hopaw.infra.memory.ILongTermMemoryService;
import com.agent.hopaw.infra.memory.LongTermMemoryService;
import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.infra.model.entity.LongTermMemory;
import com.agent.hopaw.infra.service.ChatSessionService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class MemoryManageController {

    private final ILongTermMemoryService longTermMemoryService;
    private final ChatSessionService chatSessionService;

    public MemoryManageController(LongTermMemoryService longTermMemoryService,ChatSessionService chatSessionService) {
        this.longTermMemoryService = longTermMemoryService;
        this.chatSessionService = chatSessionService;
    }

    @GetMapping("/memory-manage")
    public String page(Model model) {
        return "memory-manage";
    }

    @GetMapping("/api/memory-manage/tree")
    @ResponseBody
    public ResponseBean tree() {
        List<LongTermMemory> list = longTermMemoryService.queryUserAllMemories(null, DefaultUser.USER);
        List<Map<String, Object>> result = new ArrayList<>();
        for (LongTermMemoryTypeEnum typeEnum : LongTermMemoryTypeEnum.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", typeEnum.getCode());
            item.put("name", typeEnum.getName());
            item.put("count", list.stream().filter(m -> typeEnum.getCode().equals(m.getMemoryType())).count());
            item.put("list", list.stream().filter(m -> typeEnum.getCode().equals(m.getMemoryType())).collect(Collectors.toList()));
            result.add(item);
        }
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
    public ResponseBean create(@RequestBody Map<String, Object> body) {
        String sessionId = (String) body.get("sessionId");
        String memory = (String) body.get("memory");
        Object parentIdObj = body.get("parentId");
        Long parentId = parentIdObj != null ? Long.valueOf(parentIdObj.toString()) : null;
        String memoryType = (String) body.get("memoryType");
        String summary = (String) body.get("summary");

        LongTermMemory entity = longTermMemoryService.createMemory(sessionId, memory, parentId, DefaultUser.USER, memoryType, summary);
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
            entity.setMemoryType(body.get("memoryType"));
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
}
