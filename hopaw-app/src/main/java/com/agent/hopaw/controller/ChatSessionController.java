package com.agent.hopaw.controller;

import com.agent.hopaw.constant.DefaultUser;
import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.infra.model.entity.ChatHistory;
import com.agent.hopaw.infra.model.entity.ChatSession;
import com.agent.hopaw.infra.service.IChatSessionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/session")
public class ChatSessionController {

    private final IChatSessionService IChatSessionService;

    public ChatSessionController(IChatSessionService IChatSessionService) {
        this.IChatSessionService = IChatSessionService;
    }

    @GetMapping("/list")
    public ResponseBean list(@RequestParam(required = false) Long agentId) {
        List<ChatSession> sessions;
        if (agentId != null) {
            sessions = IChatSessionService.getSessionsByUserIdAndAgentId(DefaultUser.USER, agentId);
        } else {
            sessions = IChatSessionService.getSessionsByUserId(DefaultUser.USER);
        }
        return ResponseBean.success(sessions);
    }

    @GetMapping("/get")
    public ResponseBean get(@RequestParam String sessionId) {
        ChatSession session = IChatSessionService.getSessionBySessionId(sessionId);
        if (session == null) {
            return ResponseBean.fail("会话不存在");
        }
        return ResponseBean.success(session);
    }
    
    @GetMapping("/history")
    public ResponseBean getHistory(@RequestParam String sessionId, @RequestParam(defaultValue = "100") int limit) {
        List<ChatHistory> history = IChatSessionService.getChatHistoryBySessionId(sessionId, limit);
        return ResponseBean.success(history);
    }

    @PostMapping("/create")
    public ResponseBean create(@RequestParam Long agentId,
                              @RequestParam(required = false) String title) {
        String sessionTitle = title != null && !title.isEmpty() ? title : "新会话";
        ChatSession session = IChatSessionService.createSession(agentId, DefaultUser.USER, sessionTitle);
        return ResponseBean.success(session);
    }

    @PostMapping("/update-title")
    public ResponseBean updateTitle(@RequestParam Long id,
                                   @RequestParam String title) {
        IChatSessionService.updateSessionTitle(id, title);
        return ResponseBean.success();
    }

    @PostMapping("/delete")
    public ResponseBean delete(@RequestParam Long id) {
        IChatSessionService.deleteSession(id);
        return ResponseBean.success();
    }

    @PostMapping("/delete-by-session-id")
    public ResponseBean deleteBySessionId(@RequestParam String sessionId) {
        IChatSessionService.deleteSessionBySessionId(sessionId);
        return ResponseBean.success();
    }
}
