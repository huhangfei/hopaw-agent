package com.agent.hopaw.controller;

import com.agent.hopaw.infra.constant.UserMemoryTypeEnum;
import com.agent.hopaw.infra.mapper.AgentMapper;
import com.agent.hopaw.infra.memory.IVectorMemoryService;
import com.agent.hopaw.infra.model.dto.VectorSearchResult;
import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.infra.model.entity.Agent;
import com.agent.hopaw.infra.service.AccountService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Controller
public class VectorHistoryController {

    private final IVectorMemoryService vectorMemoryService;
    private final AgentMapper agentMapper;
    private final AccountService accountService;

    public VectorHistoryController(IVectorMemoryService vectorMemoryService, AgentMapper agentMapper,
                                   AccountService accountService) {
        this.vectorMemoryService = vectorMemoryService;
        this.agentMapper = agentMapper;
        this.accountService = accountService;
    }

    @GetMapping("/vector-history")
    public String page(Model model) {
        model.addAttribute("accounts", accountService.listAccounts());
        return "vector-history";
    }

    @GetMapping("/api/vector-history/types")
    @ResponseBody
    public ResponseBean memoryTypes() {
        List<Map<String, String>> result = new ArrayList<>();
        for (UserMemoryTypeEnum type : UserMemoryTypeEnum.values()) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("code", type.getCode());
            item.put("name", type.getName());
            result.add(item);
        }
        return ResponseBean.success(result);
    }

    @GetMapping("/api/vector-history/search")
    @ResponseBody
    public ResponseBean search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String memoryType,
            @RequestParam(defaultValue = "20") int maxResults,
            @RequestParam(defaultValue = "0.0") double minScore) {

        if (query == null || query.isBlank()) {
            return ResponseBean.fail("查询关键词不能为空");
        }
        List<VectorSearchResult> results = vectorMemoryService.search(
                query,sessionId, userId, memoryType, maxResults, minScore);

        return ResponseBean.success(results);
    }

    @DeleteMapping("/api/vector-history/{embeddingId}")
    @ResponseBody
    public ResponseBean delete(@PathVariable String embeddingId) {
        boolean ok = vectorMemoryService.deleteByEmbeddingId(embeddingId);
        if (ok) {
            return ResponseBean.success("删除成功");
        } else {
            return ResponseBean.fail("删除失败，记录不存在或已删除");
        }
    }
}