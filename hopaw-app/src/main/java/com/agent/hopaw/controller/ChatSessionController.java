package com.agent.hopaw.controller;

import com.agent.hopaw.constant.DefaultUser;
import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.infra.model.entity.ChatHistory;
import com.agent.hopaw.infra.model.entity.ChatSession;
import com.agent.hopaw.infra.model.entity.TokenUsage;
import com.agent.hopaw.infra.service.ChatSessionService;
import com.agent.hopaw.infra.service.ITokenUsageService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/session")
public class ChatSessionController {

    private final ChatSessionService chatSessionService;
    private final ITokenUsageService tokenUsageService;

    public ChatSessionController(ChatSessionService chatSessionService,
                                 ITokenUsageService tokenUsageService) {
        this.chatSessionService = chatSessionService;
        this.tokenUsageService = tokenUsageService;
    }

    @GetMapping("/list")
    public ResponseBean list(@RequestParam(required = false) Long agentId) {
        List<ChatSession> sessions;
        if (agentId != null) {
            sessions = chatSessionService.getSessionsByUserIdAndAgentId(DefaultUser.USER, agentId);
        } else {
            sessions = chatSessionService.getSessionsByUserId(DefaultUser.USER);
        }
        return ResponseBean.success(sessions);
    }

    @GetMapping("/get")
    public ResponseBean get(@RequestParam String sessionId) {
        ChatSession session = chatSessionService.getSessionBySessionId(sessionId);
        if (session == null) {
            return ResponseBean.fail("会话不存在");
        }
        return ResponseBean.success(session);
    }
    
    @GetMapping("/history")
    public ResponseBean getHistory(@RequestParam String sessionId, @RequestParam(defaultValue = "100") int limit) {
        List<ChatHistory> history = chatSessionService.getChatHistoryBySessionId(sessionId, limit);
        return ResponseBean.success(history);
    }

    @PostMapping("/create")
    public ResponseBean create(@RequestParam Long agentId,
                              @RequestParam(required = false) String title) {
        String sessionTitle = title != null && !title.isEmpty() ? title : "新会话";
        ChatSession session = chatSessionService.createSession(agentId, DefaultUser.USER, sessionTitle);
        return ResponseBean.success(session);
    }

    @PostMapping("/create-with-id")
    public ResponseBean createWithId(@RequestParam Long agentId,
                                    @RequestParam String sessionId,
                                    @RequestParam(required = false) String title) {
        String sessionTitle = title != null && !title.isEmpty() ? title : "新会话";
        ChatSession existingSession = chatSessionService.getSessionBySessionId(sessionId);
        if (existingSession != null) {
            return ResponseBean.success(existingSession);
        }
        ChatSession session = chatSessionService.createSessionWithId(agentId, DefaultUser.USER, sessionTitle, sessionId);
        return ResponseBean.success(session);
    }

    @PostMapping("/update-title")
    public ResponseBean updateTitle(@RequestParam Long id,
                                   @RequestParam String title) {
        chatSessionService.updateSessionTitle(id, title);
        return ResponseBean.success();
    }

    @PostMapping("/delete")
    public ResponseBean delete(@RequestParam Long id) {
        chatSessionService.deleteSession(id);
        return ResponseBean.success();
    }

    @PostMapping("/delete-by-session-id")
    public ResponseBean deleteBySessionId(@RequestParam String sessionId) {
        chatSessionService.deleteSessionBySessionId(sessionId);
        return ResponseBean.success();
    }

    @GetMapping("/detail")
    public ResponseBean detail(@RequestParam String sessionId) {
        ChatSession session = chatSessionService.getSessionBySessionId(sessionId);
        if (session == null) {
            return ResponseBean.fail("会话不存在");
        }

        List<ChatHistory> history = chatSessionService.getChatHistoryBySessionId(sessionId, 100);
        java.util.Collections.reverse(history);

        TokenUsage summary = tokenUsageService.summary(null, null, DefaultUser.USER, session.getAgentId(), null, "chat");

        Map<String, Object> result = new HashMap<>();
        result.put("session", session);
        result.put("history", history);
        result.put("tokenUsage", summary);
        return ResponseBean.success(result);
    }

    @PostMapping("/update-config")
    public ResponseBean updateConfig(@RequestParam String sessionId,
                                     @RequestParam Long agentId,
                                     @RequestParam(required = false) Long aiModelId,
                                     @RequestParam(required = false) Boolean enableThinking,
                                     @RequestParam(required = false) String skills) {
        chatSessionService.updateSessionConfig(sessionId, agentId, aiModelId, enableThinking, skills);
        return ResponseBean.success();
    }
}
