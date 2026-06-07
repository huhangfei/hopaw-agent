package com.agent.hopaw.controller;

import com.agent.hopaw.infra.constant.UserMemoryTypeEnum;
import com.agent.hopaw.infra.memory.ILongTermMemoryService;
import com.agent.hopaw.infra.memory.LongTermMemoryService;
import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.infra.model.entity.LongTermMemory;
import com.agent.hopaw.infra.service.ChatSessionService;
import com.agent.hopaw.util.CurrentUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class MemoryManageController {

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
}
