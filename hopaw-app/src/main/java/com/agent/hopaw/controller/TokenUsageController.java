package com.agent.hopaw.controller;

import com.agent.hopaw.infra.mapper.AgentMapper;
import com.agent.hopaw.infra.model.entity.Agent;
import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.infra.model.entity.TokenUsage;
import com.agent.hopaw.infra.service.AccountService;
import com.agent.hopaw.infra.service.TokenUsageService;
import com.agent.hopaw.util.CurrentUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Controller
public class TokenUsageController {

    private final TokenUsageService tokenUsageService;
    private final AgentMapper agentMapper;
    private final AccountService accountService;

    public TokenUsageController(TokenUsageService tokenUsageService, AgentMapper agentMapper,
                                AccountService accountService) {
        this.tokenUsageService = tokenUsageService;
        this.agentMapper = agentMapper;
        this.accountService = accountService;
    }

    @GetMapping("/token-usage")
    public String page(Model model) {
        List<Agent> agents = agentMapper.findAll();
        model.addAttribute("agents", agents);
        model.addAttribute("accounts", accountService.listAccounts());
        return "token-usage";
    }

    @GetMapping("/api/token-usage")
    @ResponseBody
    public ResponseBean query(@RequestParam("startTime") @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
                              @RequestParam("endTime") @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
                              @RequestParam(required = false) String userId,
                              @RequestParam(required = false) Long agentId,
                              @RequestParam(required = false) String modelName,
                              @RequestParam(required = false) String source,
                              @RequestParam(required = false) String sessionId,
                              @RequestParam(defaultValue = "1") int page,
                              @RequestParam(defaultValue = "10") int size) {
        Map<String, Object> data = tokenUsageService.queryPage(startTime, endTime, userId, agentId, modelName, source, sessionId, page, size);
        return ResponseBean.success(data);
    }

    @GetMapping("/api/token-usage/summary")
    @ResponseBody
    public ResponseBean summary(@RequestParam("startTime") @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
                                @RequestParam("endTime") @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
                                @RequestParam(required = false) String userId,
                                @RequestParam(required = false) Long agentId,
                                @RequestParam(required = false) String modelName,
                                @RequestParam(required = false) String source,
                                @RequestParam(required = false) String sessionId) {
        TokenUsage summary = tokenUsageService.summary(startTime, endTime, userId, agentId, modelName, source, sessionId);
        return ResponseBean.success(summary);
    }

    @GetMapping("/api/token-usage/daily-stats")
    @ResponseBody
    public ResponseBean dailyStats(@RequestParam("startTime") @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
                                   @RequestParam("endTime") @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
                                   @RequestParam(required = false) String userId,
                                   @RequestParam(required = false) Long agentId,
                                   @RequestParam(required = false) String modelName,
                                   @RequestParam(required = false) String source,
                                   @RequestParam(required = false) String sessionId) {
        List<Map<String, Object>> stats = tokenUsageService.dailyStats(startTime, endTime, userId, agentId, modelName, source, sessionId);
        return ResponseBean.success(stats);
    }

    @GetMapping("/api/token-usage/today")
    @ResponseBody
    public ResponseBean tokenUsageToday(HttpServletRequest request,
                                        @RequestParam(required = false) Long agentId,
                                        @RequestParam(required = false) String source,
                                        @RequestParam(required = false) String sessionId,
                                        @RequestParam(required = false) Long minId) {
        java.util.List<TokenUsage> list = tokenUsageService.findTodayByAgentUser(agentId, CurrentUser.require(request), source, sessionId, minId, 30);
        return ResponseBean.success(list);
    }
}
