package com.agent.hopaw.controller;

import com.agent.hopaw.avatar.service.AvatarSettingsService;
import com.agent.hopaw.infra.constant.ChatSessionTypeFilterEnum;
import com.agent.hopaw.infra.model.dto.ToolSetInfo;
import com.agent.hopaw.infra.model.entity.Agent;
import com.agent.hopaw.infra.model.entity.ChatSession;
import com.agent.hopaw.infra.service.AgentService;
import com.agent.hopaw.infra.service.IAgentExecutorService;
import com.agent.hopaw.infra.service.IChatSessionService;
import com.agent.hopaw.infra.service.IToolSetService;
import com.agent.hopaw.infra.util.UuidUtil;
import com.agent.hopaw.util.CurrentUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@Controller
public class ChatController {

    private final IChatSessionService chatSessionService;
    private final AgentService agentService;
    private final IToolSetService agentToolService;
    private final IAgentExecutorService agentExecutorService;
    private final AvatarSettingsService avatarSettingsService;

    public ChatController(IChatSessionService chatSessionService, AgentService agentService, IToolSetService agentToolService,
                          IAgentExecutorService agentExecutorService,
                          AvatarSettingsService avatarSettingsService) {
        this.chatSessionService = chatSessionService;
        this.agentService = agentService;
        this.agentToolService = agentToolService;
        this.agentExecutorService = agentExecutorService;
        this.avatarSettingsService = avatarSettingsService;
    }

    @GetMapping("/")
    public String index(@RequestParam(required = false) String sessionId, Model model, HttpServletRequest request) {
        String currentUserId = CurrentUser.require(request);
        model.addAttribute("currentUserId", currentUserId);
        model.addAttribute("agentExecutorState", false);

        // 首页可见会话：自己的聊天会话 + 所有人的项目/工作流任务会话
        List<ChatSession> chatSessions = chatSessionService.getVisibleSessions(currentUserId, null);
        // 填充会话执行器实时运行状态，首页会话列表据此显示loading图标
        chatSessions.forEach(s -> s.setRunning(agentExecutorService.isAgentExecutorRunning(s.getSessionId())));
        model.addAttribute("chatSessions", chatSessions);
        List<Agent> agents = agentService.getAgentsPage(currentUserId, null, 0, 100);
        model.addAttribute("agents", agents);
        if(sessionId == null && !chatSessions.isEmpty()){
            // 默认选中：优先自己最新的一条聊天会话，没有聊天会话才退回其他类型
            sessionId = pickDefaultSession(chatSessions, currentUserId);
        }
        Agent selectedAgent=null;
        Long aiModelId=null;
        Boolean enableThinking=true;
        String selectedSkills = "";
        String toolCallPermission = "smart_call";
        // 消息区头部显示会话标题：会话未落库（如新建未发送消息）时默认“新会话”
        String currentSessionTitle = "新会话";
        if(sessionId != null){
            ChatSession session = chatSessionService.getSessionBySessionId(sessionId);
            if(session != null){
                model.addAttribute("agentExecutorState", agentExecutorService.isAgentExecutorRunning(session.getSessionId()));
                selectedAgent=agents.stream().filter(agent -> agent.getId().equals(session.getAgentId())).findFirst().orElse(null);
                aiModelId=session.getAiModelId();
                enableThinking=session.getEnableThinking();
                selectedSkills=session.getSkillNames();
                toolCallPermission = session.getToolCallPermission();
                if(session.getTitle() != null && !session.getTitle().isBlank()){
                    currentSessionTitle = session.getTitle();
                }
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
        model.addAttribute("currentSessionTitle", currentSessionTitle);
        model.addAttribute("avatarDisabled",selectedAgent != null ? avatarSettingsService.isAvatarDisabled(currentUserId,  selectedAgent.getId()) : true);
        List<ToolSetInfo> toolSets = agentToolService.getToolSets();
        model.addAttribute("toolSets", toolSets);
        return "index";
    }

    /**
     * 首页默认选中的会话。
     *
     * <p>优先级：① 自己名下最新的聊天会话；② 没有聊天会话时，退回项目/工作流任务会话
     * （不限用户）中最新的那条。</p>
     *
     * <p>列表（{@code getVisibleSessions}）已按 last_update_time 倒序，因此每个条件取第一条即是「最新」。
     * 类型判定用 {@link ChatSessionTypeFilterEnum#match(String)}，与首页列表的归类口径一致
     * （历史遗留的 biz_type 取值也按聊天处理）。</p>
     *
     * @return 默认选中的 sessionId；列表为空或无可选会话时返回 null（前端会新建会话）
     */
    private static String pickDefaultSession(List<ChatSession> chatSessions, String currentUserId) {
        String chatSessionId = chatSessions.stream()
                .filter(s -> currentUserId.equals(s.getUserId()))
                .filter(s -> ChatSessionTypeFilterEnum.match(s.getBizType()) == ChatSessionTypeFilterEnum.CHAT)
                .map(ChatSession::getSessionId)
                .findFirst()
                .orElse(null);
        if (chatSessionId != null) {
            return chatSessionId;
        }
        return chatSessions.stream()
                .filter(s -> {
                    ChatSessionTypeFilterEnum type = ChatSessionTypeFilterEnum.match(s.getBizType());
                    return type == ChatSessionTypeFilterEnum.PROJECT || type == ChatSessionTypeFilterEnum.TASK;
                })
                .map(ChatSession::getSessionId)
                .findFirst()
                .orElse(null);
    }

}
