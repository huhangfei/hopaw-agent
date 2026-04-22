package com.agent.hopaw.controller;

import com.agent.hopaw.mapper.ChatHistoryMapper;
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
    private final List<AgentTool> allTools;

    public AgentController(AgentService agentService, ChatHistoryMapper chatHistoryMapper, List<AgentTool> allTools) {
        this.agentService = agentService;
        this.chatHistoryMapper = chatHistoryMapper;
        this.allTools = allTools;
    }

    @GetMapping("/")
    public String index(@RequestParam(required = false) Long agentId,
                       @RequestParam(required = false) String message,
                       @RequestParam(required = false) String response,
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

            if (message != null && !message.trim().isEmpty()) {
                model.addAttribute("message", message);
                model.addAttribute("response", response);

                ChatHistory chat = new ChatHistory(agentId, message, response);
                chatHistoryMapper.insert(chat);
            }
        }

        return "index";
    }

    @PostMapping("/agent/create")
    public String createAgent(@RequestParam String name,
                             @RequestParam String description,
                             @RequestParam(required = false) String tools) {
        String toolsStr = tools != null ? tools : "";
        agentService.createAgent(name, description, toolsStr);
        return "redirect:/";
    }

    @PostMapping("/agent/delete")
    public String deleteAgent(@RequestParam Long id) {
        chatHistoryMapper.deleteByAgentId(id);
        agentService.deleteAgent(id);
        return "redirect:/";
    }

    @PostMapping("/agent/update")
    public String updateAgent(@RequestParam Long id,
                             @RequestParam String name,
                             @RequestParam String description,
                             @RequestParam(required = false) String tools) {
        String toolsStr = tools != null ? tools : "";
        agentService.updateAgent(id, name, description, toolsStr);
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
        return "redirect:/?agentId=" + agentId;
    }
}
