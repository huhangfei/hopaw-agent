package com.agent.hopaw.controller;

import com.agent.hopaw.infra.memory.IVectorMemoryService;
import com.agent.hopaw.infra.model.dto.VectorSearchResult;
import com.agent.hopaw.infra.model.dto.ResponseBean;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
public class VectorHistoryController {

    private final IVectorMemoryService vectorMemoryService;

    public VectorHistoryController(IVectorMemoryService vectorMemoryService) {
        this.vectorMemoryService = vectorMemoryService;
    }
    @GetMapping("/vector-history")
    public String page() {
        return "vector-history";
    }
    
    @GetMapping("/api/vector-history/search")
    @ResponseBody
    public ResponseBean search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Long agentId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String memoryType,
            @RequestParam(defaultValue = "20") int maxResults,
            @RequestParam(defaultValue = "0.0") double minScore) {

        if (query == null || query.isBlank()) {
            return ResponseBean.fail("查询关键词不能为空");
        }

        com.agent.hopaw.infra.constant.VectorMemoryTypeEnum typeEnum = null;
        if (memoryType != null && !memoryType.isBlank()) {
            try {
                typeEnum = com.agent.hopaw.infra.constant.VectorMemoryTypeEnum.fromCode(memoryType);
            } catch (Exception e) {
                return ResponseBean.fail("无效的记忆类型: " + memoryType);
            }
        }

        List<VectorSearchResult> results = vectorMemoryService.search(
                query, agentId, userId, typeEnum, maxResults, minScore);

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
