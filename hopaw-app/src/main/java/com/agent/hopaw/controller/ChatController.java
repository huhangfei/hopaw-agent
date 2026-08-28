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
        model.addAttribute("chatFlow", Collections.emptyList());

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
                // 消息渲染流：普通消息原样、连续的工具调用合并为一组，供消息列表按行渲染工具图标
                model.addAttribute("chatFlow", buildChatFlow(chatHistory));
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

    /**
     * 将会话历史转换为消息渲染流：
     * 普通消息（text/thinking/image/error/warn 等）保持原样，
     * 连续的工具调用消息合并为一组，供消息列表按行渲染工具调用图标。
     */
    private List<Map<String, Object>> buildChatFlow(List<ChatHistoryVO> chatHistory) {
        List<Map<String, Object>> flow = new ArrayList<>();
        List<ChatHistoryVO> toolGroup = null;
        for (ChatHistoryVO chat : chatHistory) {
            if ("tool_call".equals(chat.getMessageType())) {
                // 与上一条同为工具调用：并入当前分组
                if (toolGroup == null) {
                    toolGroup = new ArrayList<>();
                    Map<String, Object> group = new HashMap<>();
                    group.put("type", "tools");
                    group.put("tools", toolGroup);
                    flow.add(group);
                }
                toolGroup.add(chat);
            } else {
                // 非工具调用消息：结束当前工具分组
                toolGroup = null;
                Map<String, Object> message = new HashMap<>();
                message.put("type", "message");
                message.put("chat", chat);
                flow.add(message);
            }
        }
        return flow;
    }

}
