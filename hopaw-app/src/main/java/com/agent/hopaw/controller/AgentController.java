package com.agent.hopaw.controller;

import com.agent.hopaw.constant.DefaultUser;
import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.infra.model.dto.ToolSetInfo;
import com.agent.hopaw.infra.model.entity.Agent;
import com.agent.hopaw.infra.model.entity.AiModel;
import com.agent.hopaw.infra.service.AgentService;
import com.agent.hopaw.infra.service.AiModelService;
import com.agent.hopaw.infra.tool.IAgentToolService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class AgentController {

    private final AgentService agentService;
    private final IAgentToolService agentToolService;
    private final AiModelService aiModelService;

    public AgentController(AgentService agentService, IAgentToolService agentToolService,
                           AiModelService aiModelService) {
        this.agentService = agentService;
        this.agentToolService = agentToolService;
        this.aiModelService = aiModelService;
    }

    @GetMapping("/agents")
    public String index(Model model) {
        model.addAttribute("activePage", "agents");
        return "agents";
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

    @GetMapping("/agent/modal/add")
    public String addAgentModal(Model model) {
        List<ToolSetInfo> toolSets = agentToolService.getToolSets();
        model.addAttribute("toolSets", toolSets);
        return "agent-form-fragments :: addAgentModal";
    }

    @GetMapping("/agent/modal/edit/{id}")
    public String editAgentModal(@PathVariable Long id, Model model) {
        Agent agent = agentService.getAgentById(id);
        List<ToolSetInfo> toolSets = agentToolService.getToolSets();
        if(agent.getAiModelId()!=null){
            AiModel aiModel = aiModelService.findById(agent.getAiModelId());
            if(aiModel!=null){
                model.addAttribute("aiModelProviderId", aiModel.getProviderId());
                model.addAttribute("aiModelId", aiModel.getId());
            }
        }
        model.addAttribute("agent", agent);
        model.addAttribute("toolSets", toolSets);
        return "agent-form-fragments :: editAgentModal";
    }

    @GetMapping("/api/agents/page")
    @ResponseBody
    public ResponseBean getAgentsPage(@RequestParam(required = false, defaultValue = "") String keyword,
                                      @RequestParam(required = false, defaultValue = "1") int page,
                                      @RequestParam(required = false, defaultValue = "10") int size) {
        List<Agent> list = agentService.getAgentsPage(DefaultUser.USER, keyword, page, size);
        int total = agentService.countAgents(DefaultUser.USER, keyword);
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return ResponseBean.success(result);
    }

    @GetMapping("/api/agents/count")
    @ResponseBody
    public ResponseBean getAgentsCount() {
        int total = agentService.countAgents(DefaultUser.USER, null);
        return ResponseBean.success(total);
    }

    @DeleteMapping("/api/agents/{id}")
    @ResponseBody
    public ResponseBean deleteAgent(@PathVariable Long id) {
        int total = agentService.countAgents(DefaultUser.USER, null);
        if (total <= 1) {
            return ResponseBean.fail("必须保留至少一个智能体");
        }
        agentService.deleteAgent(id, DefaultUser.USER);
        return ResponseBean.success();
    }
}
