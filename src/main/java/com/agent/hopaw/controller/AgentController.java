package com.agent.hopaw.controller;

import com.agent.hopaw.constant.DefaultUser;
import com.agent.hopaw.mapper.ChatHistoryMapper;
import com.agent.hopaw.mapper.ChatMemoryMapper;
import com.agent.hopaw.model.Agent;
import com.agent.hopaw.model.ChatHistory;
import com.agent.hopaw.model.ResponseBean;
import com.agent.hopaw.service.AgentService;
import com.agent.hopaw.tools.AgentTool;
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
import java.util.Comparator;
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
                .sorted(Comparator.comparing(AgentTool::getName))
                .map(t -> Map.of("name", t.getName(), "description", t.getDescription()))
                .collect(Collectors.toList());
        model.addAttribute("tools", tools);

        if (agentId != null) {
            Agent agent = agentService.getAgentById(agentId);
            model.addAttribute("selectedAgent", agent);
            model.addAttribute("selectedAgentId", agentId);

            List<ChatHistory> chatHistory = chatHistoryMapper.findByAgentId(agentId, 100);
            Collections.reverse(chatHistory);
            model.addAttribute("chatHistory", chatHistory);
            model.addAttribute("agentExecutorState", agentService.isAgentExecutorRunning(agentId,DefaultUser.USER));
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
                             @RequestParam(required = false, defaultValue = "true") Boolean enableThinking) {
        String toolsStr = tools != null ? tools : "";
        agentService.createAgent(name, description, toolsStr, maxMemoryRecords, maxToolInvocations, aiModelId, enableThinking, DefaultUser.USER);
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
        AgentService.AgentExecutor agentExecutor = agentService.getAgentExecutor(id,DefaultUser.USER);
        if(agentExecutor!=null){
            agentExecutor.stop();
            return ResponseBean.success();
        }else{
            return ResponseBean.fail("Agent Executor not found");
        }
    }

    @GetMapping("/api/agent/{id}/running")
    @ResponseBody
    public ResponseBean isRunning(@PathVariable Long id) {
        boolean running = agentService.isAgentExecutorRunning(id, DefaultUser.USER);
        return ResponseBean.success(running);
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
                             @RequestParam(required = false) Boolean enableThinking) {
        String toolsStr = tools != null ? tools : "";
        agentService.updateAgent(DefaultUser.USER,id, name, description, toolsStr, maxMemoryRecords, maxToolInvocations, aiModelId, enableThinking);
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
