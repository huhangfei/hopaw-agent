package com.agent.hopaw.controller;

import com.agent.hopaw.infra.constant.AgentExecutorBizTypeEnum;
import com.agent.hopaw.infra.constant.ChatMemoryStatusEnum;
import com.agent.hopaw.infra.memory.IChatMemoryService;
import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.infra.model.entity.ChatHistory;
import com.agent.hopaw.infra.model.entity.ChatSession;
import com.agent.hopaw.infra.service.IAgentExecutorService;
import com.agent.hopaw.infra.service.IChatHistoryService;
import com.agent.hopaw.infra.service.IChatSessionService;
import com.agent.hopaw.infra.util.UuidUtil;
import com.agent.hopaw.util.CurrentUser;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/session")
public class ChatSessionController {

    private final IChatSessionService chatSessionService;
    private final IAgentExecutorService agentExecutorService;
    private final IChatHistoryService chatHistoryService;
    private final IChatMemoryService chatMemoryService;
    private final com.agent.hopaw.infra.tool.IAgentToolService agentToolService;
    public ChatSessionController(IChatSessionService chatSessionService, IAgentExecutorService agentExecutorService, IChatHistoryService chatHistoryService, IChatMemoryService chatMemoryService, com.agent.hopaw.infra.tool.IAgentToolService agentToolService) {
        this.chatSessionService = chatSessionService;
        this.agentExecutorService = agentExecutorService;
        this.chatHistoryService = chatHistoryService;
        this.chatMemoryService = chatMemoryService;
        this.agentToolService = agentToolService;
    }

    @GetMapping("/list")
    public ResponseBean list(HttpServletRequest request, @RequestParam(required = false) Long agentId) {
        String currentUserId = CurrentUser.require(request);
        List<ChatSession> sessions;
        if (agentId != null) {
            sessions = chatSessionService.getSessionsByUserIdAndAgentId(currentUserId, agentId);
        } else {
            sessions = chatSessionService.getSessionsByUserId(currentUserId);
        }
        // 填充会话执行器实时运行状态，前端会话列表据此显示loading图标
        if (sessions != null) {
            sessions.forEach(s -> s.setRunning(agentExecutorService.isAgentExecutorRunning(s.getSessionId())));
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

    /**
     * 首页向上滚动加载更早消息：游标 (beforeTime, beforeId) 之前的会话历史（按时间倒序）
     */
    @GetMapping("/{sessionId}/history/before")
    public ResponseBean historyBefore(HttpServletRequest request,
                                      @PathVariable String sessionId,
                                      @RequestParam String beforeTime,
                                      @RequestParam Long beforeId,
                                      @RequestParam(defaultValue = "50") int limit) {
        // 会话归属校验：只能查看自己的会话历史
        ChatSession session = chatSessionService.getSessionBySessionId(sessionId);
        if (session == null || !CurrentUser.require(request).equals(session.getUserId())) {
            return ResponseBean.fail("会话不存在");
        }
        java.time.LocalDateTime cursorTime;
        try {
            cursorTime = java.time.LocalDateTime.parse(beforeTime);
        } catch (Exception e) {
            return ResponseBean.fail("时间参数格式错误");
        }
        if (limit < 1) limit = 50;
        if (limit > 100) limit = 100;
        // 多查一条判断是否还有更早数据
        List<com.agent.hopaw.infra.model.dto.ChatHistoryVO> list =
                chatHistoryService.findBySessionIdBefore(sessionId, cursorTime, beforeId, limit + 1);
        boolean hasMore = list.size() > limit;
        if (hasMore) {
            list = list.subList(0, limit);
        }
        // 工具名映射为描述（与首页服务端渲染保持一致）
        if (!list.isEmpty()) {
            Map<String, String> toolNameAndDescriptionMap = agentToolService.getToolNameAndDescriptionMap();
            list.forEach(chatHistoryVO -> {
                if (chatHistoryVO.getToolName() != null) {
                    chatHistoryVO.setToolName(toolNameAndDescriptionMap.get(chatHistoryVO.getToolName()));
                }
            });
        }
        Map<String, Object> result = new HashMap<>(2);
        result.put("list", list);
        result.put("hasMore", hasMore);
        return ResponseBean.success(result);
    }

    @PostMapping("/{sessionId}/stop")
    @ResponseBody
    public ResponseBean stopAgent(@PathVariable String sessionId) {
        agentExecutorService.stopAgentExecutor(sessionId);
        return ResponseBean.success();
    }

    @PostMapping("/{sessionId}/force-stop")
    @ResponseBody
    public ResponseBean forceStopAgent(@PathVariable String sessionId) {
        agentExecutorService.stopAndRemoveAgentExecutor(sessionId);
        return ResponseBean.success();
    }

    @PostMapping("/create")
    @ResponseBody
    public ResponseBean create(HttpServletRequest request, @RequestBody ChatSession session) {
        if(!StringUtils.hasLength(session.getTitle())){
            session.setTitle("新会话");
        }
        session.setBizType(AgentExecutorBizTypeEnum.Chat.getValue());
        session.setUserId(CurrentUser.require(request));
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


    @PostMapping("/{sessionId}/tool/stop")
    @ResponseBody
    public ResponseBean stopTool(@PathVariable String sessionId, @RequestParam String callId) {
        agentExecutorService.stopTool(sessionId, callId);
        return ResponseBean.success();
    }

    @PostMapping("/{sessionId}/tool/approval")
    @ResponseBody
    public ResponseBean approvalTool(@PathVariable String sessionId, @RequestParam String callId, @RequestParam Boolean allowed) {
        agentExecutorService.toolApprovalComplete(sessionId, callId,allowed);
        return ResponseBean.success();
    }

    @GetMapping("/{sessionId}/running")
    @ResponseBody
    public ResponseBean isRunning(@PathVariable String sessionId) {
        boolean running = agentExecutorService.isAgentExecutorRunning(sessionId);
        return ResponseBean.success(running);
    }

    /**
     * 会话清理设置页：分页查询当前用户的会话列表（含消息记录数量）
     */
    @GetMapping("/stats-page")
    @ResponseBody
    public ResponseBean statsPage(HttpServletRequest request,
                                  @RequestParam(defaultValue = "1") int page,
                                  @RequestParam(defaultValue = "20") int pageSize) {
        if (pageSize < 1) pageSize = 20;
        if (pageSize > 100) pageSize = 100;
        String userId = CurrentUser.require(request);
        return ResponseBean.success(chatSessionService.getSessionStatsPage(userId, page, pageSize));
    }

    /**
     * 批量清理会话历史（聊天记录 + 记忆），会话本身保留
     */
    @PostMapping("/batch-clear")
    @ResponseBody
    public ResponseBean batchClear(@RequestBody Map<String, List<String>> body) {
        List<String> sessionIds = body.get("sessionIds");
        if (sessionIds == null || sessionIds.isEmpty()) {
            return ResponseBean.fail("请选择要清理的会话");
        }
        int success = 0;
        List<String> failed = new ArrayList<>();
        for (String sessionId : sessionIds) {
            try {
                chatHistoryService.deleteBySessionId(sessionId);
                chatMemoryService.clear(sessionId);
                success++;
            } catch (Exception e) {
                failed.add(sessionId);
            }
        }
        Map<String, Object> result = new HashMap<>(2);
        result.put("success", success);
        result.put("failed", failed);
        return ResponseBean.success(result);
    }

    /**
     * 批量删除会话：先清理历史（聊天记录 + 记忆），再删除会话本身
     */
    @PostMapping("/batch-delete")
    @ResponseBody
    public ResponseBean batchDelete(@RequestBody Map<String, List<String>> body) {
        List<String> sessionIds = body.get("sessionIds");
        if (sessionIds == null || sessionIds.isEmpty()) {
            return ResponseBean.fail("请选择要删除的会话");
        }
        int success = 0;
        List<String> failed = new ArrayList<>();
        for (String sessionId : sessionIds) {
            try {
                // 先清理历史与记忆
                chatHistoryService.deleteBySessionId(sessionId);
                chatMemoryService.clear(sessionId);
                // 再删除会话
                chatSessionService.deleteSessionBySessionId(sessionId);
                success++;
            } catch (Exception e) {
                failed.add(sessionId);
            }
        }
        Map<String, Object> result = new HashMap<>(2);
        result.put("success", success);
        result.put("failed", failed);
        return ResponseBean.success(result);
    }

    /**
     * 会话工具调用统计：会话累计调用总数、当前执行器已执行数量、执行器最大调用次数
     */
    @GetMapping("/{sessionId}/tool-stats")
    @ResponseBody
    public ResponseBean toolStats(@PathVariable String sessionId) {
        Map<String, Object> stats = new HashMap<>(4);
        // 统计1：该会话所有工具调用总数（含历史）
        stats.put("sessionTotal", chatHistoryService.countToolCallsBySessionId(sessionId));
        // 统计2/3：当前执行器的执行数量与上限（执行器不存在时为0/0）
        var executor = agentExecutorService.getAgentExecutor(sessionId);
        if (executor != null) {
            stats.put("executedCount", executor.getExecutedToolCount());
            stats.put("maxToolInvocations", executor.getMaxToolInvocations());
        } else {
            stats.put("executedCount", 0);
            stats.put("maxToolInvocations", 0);
        }
        return ResponseBean.success(stats);
    }

    @PostMapping("/{sessionId}/clear")
    @ResponseBody
    public ResponseBean clearChat(@PathVariable String sessionId) {
        chatHistoryService.deleteBySessionId(sessionId);
        chatMemoryService.clear(sessionId);
        return ResponseBean.success();
    }
}
