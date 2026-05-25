package com.agent.hopaw.controller;

import com.agent.hopaw.constant.DefaultUser;
import com.agent.hopaw.infra.mapper.ChatHistoryMapper;
import com.agent.hopaw.infra.mapper.ChatMemoryMapper;
import com.agent.hopaw.infra.model.entity.Agent;
import com.agent.hopaw.infra.model.entity.ChatHistory;
import com.agent.hopaw.infra.model.entity.ChatSession;
import com.agent.hopaw.infra.model.entity.TokenUsage;
import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.infra.model.dto.ToolSetInfo;
import com.agent.hopaw.infra.service.AgentService;
import com.agent.hopaw.infra.service.ChatSessionService;
import com.agent.hopaw.infra.service.IAgentExecutorService;
import com.agent.hopaw.infra.service.ITokenUsageService;
import com.agent.hopaw.infra.tool.IAgentToolService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Controller
public class AgentController {

    private final AgentService agentService;
    private final IAgentToolService agentToolService;
    private final ChatHistoryMapper chatHistoryMapper;
    private final ChatMemoryMapper chatMemoryMapper;
    private final IAgentExecutorService agentExecutorService;
    private final ChatSessionService chatSessionService;
    private final ITokenUsageService tokenUsageService;

    public AgentController(AgentService agentService, IAgentToolService agentToolService,
                           ChatHistoryMapper chatHistoryMapper, ChatMemoryMapper chatMemoryMapper,
                           IAgentExecutorService agentExecutorService,
                           ChatSessionService chatSessionService,
                           ITokenUsageService tokenUsageService) {
        this.agentService = agentService;
        this.agentToolService = agentToolService;
        this.chatHistoryMapper = chatHistoryMapper;
        this.chatMemoryMapper = chatMemoryMapper;
        this.agentExecutorService = agentExecutorService;
        this.chatSessionService = chatSessionService;
        this.tokenUsageService = tokenUsageService;
    }

    @GetMapping("/")
    public String index(@RequestParam(required = false) String sessionId,
                       Model model) {
        List<Agent> agents = agentService.getAllAgents();
        model.addAttribute("agents", agents);

        List<ToolSetInfo> toolSets = agentToolService.getToolSets();
        model.addAttribute("toolSets", toolSets);

        List<ChatSession> sessions = chatSessionService.getSessionsByUserId(DefaultUser.USER);
        model.addAttribute("sessions", sessions);

        if (!sessions.isEmpty()) {
            ChatSession selectedSession;
            if (sessionId != null && !sessionId.isEmpty()) {
                selectedSession = chatSessionService.getSessionBySessionId(sessionId);
            } else {
                selectedSession = sessions.get(0);
            }

            if (selectedSession != null) {
                model.addAttribute("selectedSession", selectedSession);

                List<ChatHistory> chatHistory = chatSessionService.getChatHistoryBySessionId(selectedSession.getSessionId(), 100);
                Collections.reverse(chatHistory);
                model.addAttribute("chatHistory", chatHistory);

                TokenUsage summary = tokenUsageService.summary(null, null, DefaultUser.USER, selectedSession.getAgentId(), null, "chat");
                model.addAttribute("tokenUsage", summary);

                model.addAttribute("agentExecutorState", agentService.isAgentExecutorRunning(selectedSession.getAgentId(), DefaultUser.USER));
            }
        }

        return "index";
    }

    @PostMapping("/agent/create")
    public String createAgent(@RequestParam String name,
                             @RequestParam String description,
                             @RequestParam(required = false) String tools,
                             @RequestParam(required = false, defaultValue = "20") Integer maxMemoryRecords,
                             @RequestParam(required = false, defaultValue = "10") Integer maxToolInvocations,
                             @RequestParam Long aiModelId,
                             @RequestParam(required = false, defaultValue = "true") Boolean enableThinking,
                             @RequestParam(required = false, defaultValue = "true") Boolean vectorToolSearch,
                             @RequestParam(required = false, defaultValue = "5") Integer vectorToolSearchMaxResults) {
        String toolsStr = tools != null ? tools : "";
        agentService.createAgent(name, description, toolsStr, maxMemoryRecords, maxToolInvocations, aiModelId, enableThinking, vectorToolSearch, vectorToolSearchMaxResults, DefaultUser.USER);
        return "redirect:/";
    }

    @PostMapping("/agent/delete")
    public String deleteAgent(@RequestParam Long id) {
        chatHistoryMapper.deleteByAgentId(id);
        chatMemoryMapper.deleteByAgentId(id);
        agentService.deleteAgent(id,DefaultUser.USER);
        return "redirect:/";
    }
    @PostMapping("/agent/stop")
    @ResponseBody
    public ResponseBean stopAgent(@RequestParam Long id) {
        agentService.stopAgentExecutor(id,DefaultUser.USER);
        return ResponseBean.success();
    }

    @PostMapping("/agent/force-stop")
    @ResponseBody
    public ResponseBean forceStopAgent(@RequestParam Long id) {
        agentExecutorService.stopAndRemoveAgentExecutor(id, DefaultUser.USER);
        return ResponseBean.success();
    }

    @PostMapping("/agent/tool/stop")
    @ResponseBody
    public ResponseBean stopTool(@RequestParam Long agentId, @RequestParam String callId) {
        agentExecutorService.stopTool(agentId, DefaultUser.USER, callId);
        return ResponseBean.success();
    }

    @GetMapping("/api/agent/{id}/running")
    @ResponseBody
    public ResponseBean isRunning(@PathVariable Long id) {
        boolean running = agentService.isAgentExecutorRunning(id, DefaultUser.USER);
        return ResponseBean.success(running);
    }

    @GetMapping("/api/agents")
    @ResponseBody
    public ResponseBean listAgents() {
        List<Agent> agents = agentService.getAllAgents();
        return ResponseBean.success(agents);
    }

    @PutMapping("/api/agents/{id}/thinking")
    @ResponseBody
    public ResponseBean updateThinking(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        Boolean enabled = body.get("enabled");
        if (enabled == null) {
            return ResponseBean.fail("参数错误");
        }
        agentService.updateThinking(id, enabled,DefaultUser.USER);
        return ResponseBean.success();
    }

    @PostMapping("/agent/update")
    public String updateAgent(@RequestParam Long id,
                             @RequestParam String name,
                             @RequestParam String description,
                             @RequestParam(required = false) String tools,
                             @RequestParam(required = false, defaultValue = "20") Integer maxMemoryRecords,
                             @RequestParam(required = false, defaultValue = "10") Integer maxToolInvocations,
                             @RequestParam Long aiModelId,
                             @RequestParam(required = false) Boolean enableThinking,
                             @RequestParam(required = false, defaultValue = "true") Boolean vectorToolSearch,
                             @RequestParam(required = false, defaultValue = "5") Integer vectorToolSearchMaxResults) {
        String toolsStr = tools != null ? tools : "";
        agentService.updateAgent(DefaultUser.USER,id, name, description, toolsStr, maxMemoryRecords, maxToolInvocations, aiModelId, enableThinking, vectorToolSearch, vectorToolSearchMaxResults);
        return "redirect:/?agentId=" + id;
    }

    @PostMapping("/chat")
    public String chat(@RequestParam Long agentId,
                      Model model) {
        Agent agent = agentService.getAgentById(agentId);
        return "redirect:/?agentId=" + agentId;
    }



    @GetMapping("/chat/clear")
    public String clearChat(@RequestParam Long agentId) {
        chatHistoryMapper.deleteByAgentId(agentId);
        chatMemoryMapper.updateStatusByAgentId(agentId,2);
        return "redirect:/?agentId=" + agentId;
    }
}
