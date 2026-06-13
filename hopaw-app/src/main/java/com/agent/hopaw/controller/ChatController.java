package com.agent.hopaw.controller;

import com.agent.hopaw.avatar.service.AvatarSettingsService;
import com.agent.hopaw.infra.model.dto.ChatHistoryVO;
import com.agent.hopaw.infra.model.dto.ToolSetInfo;
import com.agent.hopaw.infra.model.entity.Agent;
import com.agent.hopaw.infra.model.entity.ChatSession;
import com.agent.hopaw.infra.service.AgentService;
import com.agent.hopaw.infra.service.IAgentExecutorService;
import com.agent.hopaw.infra.service.IChatHistoryService;
import com.agent.hopaw.infra.service.IChatSessionService;
import com.agent.hopaw.infra.tool.IAgentToolService;
import com.agent.hopaw.infra.util.UuidUtil;
import com.agent.hopaw.util.CurrentUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

@Controller
public class ChatController {

    private final IChatSessionService chatSessionService;
    private final AgentService agentService;
    private final IAgentToolService agentToolService;
    private final IChatHistoryService chatHistoryService;
    private final IAgentExecutorService agentExecutorService;
    private final AvatarSettingsService avatarSettingsService;

    public ChatController(IChatSessionService chatSessionService, AgentService agentService, IAgentToolService agentToolService,
                          IChatHistoryService chatHistoryService,
                          IAgentExecutorService agentExecutorService,
                          AvatarSettingsService avatarSettingsService) {
        this.chatSessionService = chatSessionService;
        this.agentService = agentService;
        this.agentToolService = agentToolService;
        this.chatHistoryService = chatHistoryService;
        this.agentExecutorService = agentExecutorService;
        this.avatarSettingsService = avatarSettingsService;
    }

    @GetMapping("/")
    public String index(@RequestParam(required = false) String sessionId, Model model, HttpServletRequest request) {
        String currentUserId = CurrentUser.require(request);
        model.addAttribute("currentUserId", currentUserId);
        model.addAttribute("agentExecutorState", false);
        model.addAttribute("chatHistory", Collections.emptyList());

        List<ChatSession> chatSessions = chatSessionService.getSessionsByUserId(currentUserId);
        model.addAttribute("chatSessions", chatSessions);
        List<Agent> agents = agentService.getAgentsPage(currentUserId, null, 0, 100);
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
                if(!chatHistory.isEmpty()){
                    Map<String, String> toolNameAndDescriptionMap = agentToolService.getToolNameAndDescriptionMap();
                    chatHistory.forEach(chatHistoryVO -> {
                        if(chatHistoryVO.getToolName() != null){
                            chatHistoryVO.setToolName(toolNameAndDescriptionMap.get(chatHistoryVO.getToolName()));
                        }
                    });
                }


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
        if(selectedAgent!=null && aiModelId == null){
            aiModelId=selectedAgent.getAiModelId();
        }
        model.addAttribute("selectedAgent", selectedAgent);
        model.addAttribute("selectedAgentId", selectedAgent != null ? selectedAgent.getId() : null);
        model.addAttribute("selectedSkills", selectedSkills);
        model.addAttribute("selectedAiModelId", aiModelId);
        model.addAttribute("enableThinking", enableThinking);
        model.addAttribute("toolCallPermission", toolCallPermission);
        model.addAttribute("currentSessionId", sessionId==null? UuidUtil.generateSimpleUUID() :sessionId);
        model.addAttribute("avatarDisabled",selectedAgent != null ? avatarSettingsService.isAvatarDisabled(currentUserId,  selectedAgent.getId()) : true);
        List<ToolSetInfo> toolSets = agentToolService.getToolSets();
        model.addAttribute("toolSets", toolSets);
        return "index";
    }


}
