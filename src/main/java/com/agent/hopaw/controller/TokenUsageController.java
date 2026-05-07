package com.agent.hopaw.controller;

import com.agent.hopaw.mapper.AgentMapper;
import com.agent.hopaw.model.Agent;
import com.agent.hopaw.model.ResponseBean;
import com.agent.hopaw.model.TokenUsage;
import com.agent.hopaw.service.TokenUsageService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Controller
public class TokenUsageController {

    private final TokenUsageService tokenUsageService;
    private final AgentMapper agentMapper;

    public TokenUsageController(TokenUsageService tokenUsageService, AgentMapper agentMapper) {
        this.tokenUsageService = tokenUsageService;
        this.agentMapper = agentMapper;
    }

    @GetMapping("/token-usage")
    public String page(Model model) {
        List<Agent> agents = agentMapper.findAll();
        model.addAttribute("agents", agents);
        return "token-usage";
    }

    @GetMapping("/api/token-usage")
    @ResponseBody
    public ResponseBean query(@RequestParam("startTime") @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
                              @RequestParam("endTime") @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
                              @RequestParam(required = false) String userId,
                              @RequestParam(required = false) Long agentId,
                              @RequestParam(required = false) String source,
                              @RequestParam(defaultValue = "1") int page,
                              @RequestParam(defaultValue = "10") int size) {
        Map<String, Object> data = tokenUsageService.queryPage(startTime, endTime, userId, agentId, source, page, size);
        return ResponseBean.success(data);
    }

    @GetMapping("/api/token-usage/summary")
    @ResponseBody
    public ResponseBean summary(@RequestParam("startTime") @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
                                @RequestParam("endTime") @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
                                @RequestParam(required = false) String userId,
                                @RequestParam(required = false) Long agentId,
                                @RequestParam(required = false) String source) {
        TokenUsage summary = tokenUsageService.summary(startTime, endTime, userId, agentId, source);
        return ResponseBean.success(summary);
    }

    @GetMapping("/api/token-usage/daily-stats")
    @ResponseBody
    public ResponseBean dailyStats(@RequestParam("startTime") @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
                                   @RequestParam("endTime") @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
                                   @RequestParam(required = false) String userId,
                                   @RequestParam(required = false) Long agentId,
                                   @RequestParam(required = false) String source) {
        List<Map<String, Object>> stats = tokenUsageService.dailyStats(startTime, endTime, userId, agentId, source);
        return ResponseBean.success(stats);
    }
}
