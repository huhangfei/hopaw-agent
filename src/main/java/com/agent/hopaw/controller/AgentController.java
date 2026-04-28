package com.agent.hopaw.controller;

import com.agent.hopaw.mapper.ChatHistoryMapper;
import com.agent.hopaw.mapper.ChatMemoryMapper;
import com.agent.hopaw.model.Agent;
import com.agent.hopaw.model.ChatHistory;
import com.agent.hopaw.service.AgentService;
import com.agent.hopaw.tools.AgentTool;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class AgentController {

    private final AgentService agentService;
    private final ChatHistoryMapper chatHistoryMapper;
    private final ChatMemoryMapper chatMemoryMapper;
    private final List<AgentTool> allTools;

    public AgentController(AgentService agentService, ChatHistoryMapper chatHistoryMapper, ChatMemoryMapper chatMemoryMapper, List<AgentTool> allTools) {
        this.agentService = agentService;
        this.chatHistoryMapper = chatHistoryMapper;
        this.chatMemoryMapper = chatMemoryMapper;
        this.allTools = allTools;
    }

    @GetMapping("/")
    public String index(@RequestParam(required = false) Long agentId,
                       Model model) {
        List<Agent> agents = agentService.getAllAgents();
        model.addAttribute("agents", agents);

        List<Map<String, String>> tools = allTools.stream()
                .map(t -> Map.of("name", t.getName(), "description", t.getDescription()))
                .collect(Collectors.toList());
        model.addAttribute("tools", tools);

        if (agentId != null) {
            Agent agent = agentService.getAgentById(agentId);
            model.addAttribute("selectedAgent", agent);
            model.addAttribute("selectedAgentId", agentId);

            List<ChatHistory> chatHistory = chatHistoryMapper.findByAgentId(agentId);
            model.addAttribute("chatHistory", chatHistory);
        }

        return "index";
    }

    @PostMapping("/agent/create")
    public String createAgent(@RequestParam String name,
                             @RequestParam String description,
                             @RequestParam(required = false) String tools,
                             @RequestParam(required = false, defaultValue = "20") Integer maxMemoryRecords,
                             @RequestParam(required = false, defaultValue = "10") Integer maxToolInvocations) {
        String toolsStr = tools != null ? tools : "";
        agentService.createAgent(name, description, toolsStr, maxMemoryRecords, maxToolInvocations);
        return "redirect:/";
    }

    @PostMapping("/agent/delete")
    public String deleteAgent(@RequestParam Long id) {
        chatHistoryMapper.deleteByAgentId(id);
        chatMemoryMapper.deleteByAgentId(id);
        agentService.deleteAgent(id);
        return "redirect:/";
    }

    @PostMapping("/agent/update")
    public String updateAgent(@RequestParam Long id,
                             @RequestParam String name,
                             @RequestParam String description,
                             @RequestParam(required = false) String tools,
                             @RequestParam(required = false, defaultValue = "20") Integer maxMemoryRecords,
                             @RequestParam(required = false, defaultValue = "10") Integer maxToolInvocations) {
        String toolsStr = tools != null ? tools : "";
        agentService.updateAgent(id, name, description, toolsStr, maxMemoryRecords, maxToolInvocations);
        return "redirect:/";
    }

    @PostMapping("/chat")
    public String chat(@RequestParam Long agentId,
                      @RequestParam String message,
                      Model model) {
        Agent agent = agentService.getAgentById(agentId);
        String response = "";

        if (agent != null) {
            AgentService.AgentExecutor executor = agentService.getAgentExecutor(agentId);
            if (executor != null) {
                response = executor.execute(message);
            }
        }

        return "redirect:/?agentId=" + agentId + "&message=" + java.net.URLEncoder.encode(message) + "&response=" + java.net.URLEncoder.encode(response);
    }

    @GetMapping("/chat/clear")
    public String clearChat(@RequestParam Long agentId) {
        chatHistoryMapper.deleteByAgentId(agentId);
        chatMemoryMapper.deleteByAgentId(agentId);
        return "redirect:/?agentId=" + agentId;
    }
}
