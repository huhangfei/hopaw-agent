package com.agent.hopaw.controller;

import com.agent.hopaw.constant.DefaultUser;
import com.agent.hopaw.mapper.AgentMapper;
import com.agent.hopaw.model.Agent;
import com.agent.hopaw.model.LongTermMemory;
import com.agent.hopaw.model.ResponseBean;
import com.agent.hopaw.service.LongTermMemoryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
public class MemoryManageController {

    private final LongTermMemoryService longTermMemoryService;
    private final AgentMapper agentMapper;

    public MemoryManageController(LongTermMemoryService longTermMemoryService, AgentMapper agentMapper) {
        this.longTermMemoryService = longTermMemoryService;
        this.agentMapper = agentMapper;
    }

    @GetMapping("/memory-manage")
    public String page(Model model) {
        List<Agent> agents = agentMapper.findByUserId(DefaultUser.USER);
        model.addAttribute("agents", agents);
        return "memory-manage";
    }

    @GetMapping("/api/memory-manage/tree")
    @ResponseBody
    public ResponseBean tree(@RequestParam String agentId) {
        List<LongTermMemory> list = longTermMemoryService.getAllMemoriesByAgentId(agentId, DefaultUser.USER);
        return ResponseBean.success(list);
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
        String agentId = (String) body.get("agentId");
        String memory = (String) body.get("memory");
        Object parentIdObj = body.get("parentId");
        Long parentId = parentIdObj != null ? Long.valueOf(parentIdObj.toString()) : null;

        LongTermMemory entity = longTermMemoryService.createMemory(agentId, memory, parentId, DefaultUser.USER);
        return ResponseBean.success(entity);
    }

    @PutMapping("/api/memory-manage/{id}")
    @ResponseBody
    public ResponseBean update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String memory = body.get("memory");
        longTermMemoryService.updateMemory(id, memory);
        return ResponseBean.success(null);
    }

    @PutMapping("/api/memory-manage/{id}/move")
    @ResponseBody
    public ResponseBean move(@PathVariable Long id, @RequestParam(required = false) Long newParentId) {
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
