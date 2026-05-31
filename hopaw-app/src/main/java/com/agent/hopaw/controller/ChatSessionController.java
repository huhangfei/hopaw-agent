package com.agent.hopaw.controller;

import com.agent.hopaw.constant.DefaultUser;
import com.agent.hopaw.infra.constant.ChatMemoryStatusEnum;
import com.agent.hopaw.infra.memory.IChatMemoryService;
import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.infra.model.entity.ChatHistory;
import com.agent.hopaw.infra.model.entity.ChatSession;
import com.agent.hopaw.infra.service.IAgentExecutorService;
import com.agent.hopaw.infra.service.IChatHistoryService;
import com.agent.hopaw.infra.service.IChatSessionService;
import com.agent.hopaw.infra.util.UuidUtil;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/session")
public class ChatSessionController {

    private final IChatSessionService chatSessionService;
    private final IAgentExecutorService agentExecutorService;
    private final IChatHistoryService chatHistoryService;
    private final IChatMemoryService chatMemoryService;
    public ChatSessionController(IChatSessionService chatSessionService, IAgentExecutorService agentExecutorService, IChatHistoryService chatHistoryService, IChatMemoryService chatMemoryService) {
        this.chatSessionService = chatSessionService;
        this.agentExecutorService = agentExecutorService;
        this.chatHistoryService = chatHistoryService;
        this.chatMemoryService = chatMemoryService;
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

    @PostMapping("/stop")
    @ResponseBody
    public ResponseBean stopAgent(@RequestParam String sessionId) {
        agentExecutorService.stopAgentExecutor(sessionId);
        return ResponseBean.success();
    }

    @PostMapping("/force-stop")
    @ResponseBody
    public ResponseBean forceStopAgent(@RequestParam String sessionId) {
        agentExecutorService.stopAndRemoveAgentExecutor(sessionId);
        return ResponseBean.success();
    }

    @PostMapping("/create")
    @ResponseBody
    public ResponseBean create(@RequestBody ChatSession session) {
        if(!StringUtils.hasLength(session.getTitle())){
            session.setTitle("新任务");
        }
        session.setUserId(DefaultUser.USER);
        session.setSessionId(UuidUtil.generateSimpleUUID());
        session.setCreateTime(java.time.LocalDateTime.now());
        session.setLastUpdateTime(java.time.LocalDateTime.now());
        chatSessionService.insertSession(session);
        return ResponseBean.success(session.getSessionId());
    }

    @PostMapping("/update-title")
    public ResponseBean updateTitle(@RequestParam Long id,
                                   @RequestParam String title) {
        chatSessionService.updateSessionTitle(id, title);
        return ResponseBean.success();
    }

    @DeleteMapping("/{id}")
    public ResponseBean delete(@PathVariable Long id) {
        ChatSession session = chatSessionService.getSessionById(id);
        chatSessionService.deleteSession(id);
        chatHistoryService.deleteBySessionId(session.getSessionId());
        chatMemoryService.clear(session.getSessionId());
        return ResponseBean.success();
    }

    @DeleteMapping("/{sessionId}/delete-by-session-id")
    public ResponseBean deleteBySessionId(@PathVariable String sessionId) {
        chatSessionService.deleteSessionBySessionId(sessionId);
        chatHistoryService.deleteBySessionId(sessionId);
        chatMemoryService.clear(sessionId);
        return ResponseBean.success();
    }


    @PostMapping("/tool/stop")
    @ResponseBody
    public ResponseBean stopTool(@RequestParam String sessionId, @RequestParam String callId) {
        agentExecutorService.stopTool(sessionId, callId);
        return ResponseBean.success();
    }

    @GetMapping("/{sessionId}/running")
    @ResponseBody
    public ResponseBean isRunning(@PathVariable String sessionId) {
        boolean running = agentExecutorService.isAgentExecutorRunning(sessionId);
        return ResponseBean.success(running);
    }

    @PostMapping("/{sessionId}/clear")
    @ResponseBody
    public ResponseBean clearChat(@PathVariable String sessionId) {
        chatHistoryService.deleteBySessionId(sessionId);
        chatMemoryService.clear(sessionId);
        return ResponseBean.success();
    }
}
