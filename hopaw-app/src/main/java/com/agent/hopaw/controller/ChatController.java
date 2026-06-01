package com.agent.hopaw.controller;

import com.agent.hopaw.constant.DefaultUser;
import com.agent.hopaw.infra.model.dto.ChatHistoryVO;
import com.agent.hopaw.infra.model.dto.ToolSetInfo;
import com.agent.hopaw.infra.model.entity.Agent;
import com.agent.hopaw.infra.model.entity.ChatSession;
import com.agent.hopaw.infra.service.AgentService;
import com.agent.hopaw.infra.service.IAgentExecutorService;
import com.agent.hopaw.infra.service.IChatHistoryService;
import com.agent.hopaw.infra.service.IChatSessionService;
import com.agent.hopaw.infra.tool.IAgentToolService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Controller
public class ChatController {

    private final IChatSessionService chatSessionService;
    private final AgentService agentService;
    private final IAgentToolService agentToolService;
    private final IChatHistoryService chatHistoryService;
    private final IAgentExecutorService agentExecutorService;

    public ChatController(IChatSessionService chatSessionService, AgentService agentService, IAgentToolService agentToolService,
                          IChatHistoryService chatHistoryService,
                          IAgentExecutorService agentExecutorService) {
        this.chatSessionService = chatSessionService;
        this.agentService = agentService;
        this.agentToolService = agentToolService;
        this.chatHistoryService = chatHistoryService;
        this.agentExecutorService = agentExecutorService;
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
        String selectedSkills = "";
        String toolCallPermission = "smart_call";
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
                selectedSkills=session.getSkillNames();
                toolCallPermission = session.getToolCallPermission();
            }
        }
        if(selectedAgent==null && !agents.isEmpty()){
            selectedAgent=agents.get(0);
        }
        if(aiModelId == null){
            aiModelId=selectedAgent.getAiModelId();
        }
        model.addAttribute("selectedAgent", selectedAgent);
        model.addAttribute("selectedSkills", selectedSkills);
        model.addAttribute("selectedAgentId", selectedAgent.getId());
        model.addAttribute("selectedAiModelId", aiModelId);
        model.addAttribute("enableThinking", enableThinking);
        model.addAttribute("toolCallPermission", toolCallPermission);
        model.addAttribute("currentSessionId", sessionId==null? UUID.randomUUID().toString() :sessionId);
        List<ToolSetInfo> toolSets = agentToolService.getToolSets();
        model.addAttribute("toolSets", toolSets);
        return "index";
    }


}
