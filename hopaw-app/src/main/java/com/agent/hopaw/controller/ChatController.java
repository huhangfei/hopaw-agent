package com.agent.hopaw.controller;

import com.agent.hopaw.constant.DefaultUser;
import com.agent.hopaw.infra.mapper.ChatHistoryMapper;
import com.agent.hopaw.infra.mapper.ChatMemoryMapper;
import com.agent.hopaw.infra.model.dto.ChatHistoryVO;
import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.infra.model.dto.ToolSetInfo;
import com.agent.hopaw.infra.model.entity.Agent;
import com.agent.hopaw.infra.model.entity.ChatHistory;
import com.agent.hopaw.infra.model.entity.ChatSession;
import com.agent.hopaw.infra.service.AgentService;
import com.agent.hopaw.infra.service.IAgentExecutorService;
import com.agent.hopaw.infra.service.IChatHistoryService;
import com.agent.hopaw.infra.service.IChatSessionService;
import com.agent.hopaw.infra.tool.IAgentToolService;
import com.agent.hopaw.infra.util.UuidUtil;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
public class ChatController {

    private final IChatSessionService chatSessionService;
    private final AgentService agentService;
    private final IAgentToolService agentToolService;
    private final IChatHistoryService chatHistoryService;
    private final ChatMemoryMapper chatMemoryMapper;
    private final IAgentExecutorService agentExecutorService;

    public ChatController(IChatSessionService chatSessionService, AgentService agentService, IAgentToolService agentToolService,
                          IChatHistoryService chatHistoryService, ChatMemoryMapper chatMemoryMapper,
                          IAgentExecutorService agentExecutorService) {
        this.chatSessionService = chatSessionService;
        this.agentService = agentService;
        this.agentToolService = agentToolService;
        this.chatHistoryService = chatHistoryService;
        this.chatMemoryMapper = chatMemoryMapper;
        this.agentExecutorService = agentExecutorService;
    }
    @GetMapping("/chat/newSession")
    public String newSession(@RequestParam(required = false) Long agentId,
                             @RequestParam(required = false) String skillNames,
                             @RequestParam(required = false) Long aiModelId,
                             @RequestParam(required = false) Boolean enableThinking) {
        String sessionId = UuidUtil.generateSimpleUUID();
        ChatSession session = chatSessionService.createSessionWithId(DefaultUser.USER, "新任务", sessionId);
        if (agentId != null) session.setAgentId(agentId);
        if (skillNames != null && !skillNames.isEmpty()) session.setSkillNames(skillNames);
        if (aiModelId != null) session.setAiModelId(aiModelId);
        if (enableThinking != null) session.setEnableThinking(enableThinking);
        if (agentId != null || skillNames != null || aiModelId != null || enableThinking != null) {
            chatSessionService.updateSession(session);
        }
        return "redirect:/?sessionId=" + sessionId;
    }
    @GetMapping("/")
    public String index(@RequestParam(required = false) String sessionId,Model model) {
        model.addAttribute("agentExecutorState", false);
        model.addAttribute("chatHistory", Collections.emptyList());

        List<ChatSession> chatSessions = chatSessionService.getSessionsByUserId(DefaultUser.USER);
        model.addAttribute("chatSessions", chatSessions);

        List<Agent> agents = agentService.getAllAgents();
        model.addAttribute("agents", agents);
        if(sessionId == null && !chatSessions.isEmpty()){
            sessionId=chatSessions.get(0).getSessionId();
        }
        Agent selectedAgent=null;
        Long aiModelId=null;
        Boolean enableThinking=true;
        model.addAttribute("chatHistory", Collections.emptyList());
        if(sessionId != null){
            ChatSession session = chatSessionService.getSessionBySessionId(sessionId);
            if(session != null){
                List<ChatHistoryVO> chatHistory = chatHistoryService.findBySessionId(sessionId, 100);
                Collections.reverse(chatHistory);
                model.addAttribute("chatHistory", chatHistory);
                model.addAttribute("agentExecutorState", agentExecutorService.isAgentExecutorRunning(session.getSessionId()));
                selectedAgent=agents.stream().filter(agent -> agent.getId().equals(session.getAgentId())).findFirst().orElse(null);
                aiModelId=session.getAiModelId();
                enableThinking=session.getEnableThinking();
            }
        }
        if(selectedAgent==null && !agents.isEmpty()){
            selectedAgent=agents.get(0);
        }
        if(aiModelId == null){
            aiModelId=selectedAgent.getAiModelId();
        }
        model.addAttribute("selectedAgent", selectedAgent);
        model.addAttribute("selectedAgentId", selectedAgent.getId());
        model.addAttribute("selectedAiModelId", aiModelId);
        model.addAttribute("enableThinking", enableThinking);
        model.addAttribute("currentSessionId", sessionId==null? UUID.randomUUID().toString() :sessionId);
        List<ToolSetInfo> toolSets = agentToolService.getToolSets();
        model.addAttribute("toolSets", toolSets);
        return "index";
    }

    @PostMapping("/chat/session/stop")
    @ResponseBody
    public ResponseBean stopAgent(@RequestParam String sessionId) {
        agentExecutorService.stopAgentExecutor(sessionId);
        return ResponseBean.success();
    }

    @PostMapping("/chat/session/force-stop")
    @ResponseBody
    public ResponseBean forceStopAgent(@RequestParam String sessionId) {
        agentExecutorService.stopAndRemoveAgentExecutor(sessionId);
        return ResponseBean.success();
    }

    @PostMapping("/chat/session/tool/stop")
    @ResponseBody
    public ResponseBean stopTool(@RequestParam String sessionId, @RequestParam String callId) {
        agentExecutorService.stopTool(sessionId, callId);
        return ResponseBean.success();
    }

    @GetMapping("/chat/session/{sessionId}/running")
    @ResponseBody
    public ResponseBean isRunning(@PathVariable String sessionId) {
        boolean running = agentExecutorService.isAgentExecutorRunning(sessionId);
        return ResponseBean.success(running);
    }

    @GetMapping("/chat/clear")
    public String clearChat(@RequestParam String sessionId) {
        chatHistoryService.deleteBySessionId(sessionId);
        chatMemoryMapper.updateStatusBySessionId(sessionId,2);
        return "redirect:/?sessionId=" + sessionId;
    }
}
