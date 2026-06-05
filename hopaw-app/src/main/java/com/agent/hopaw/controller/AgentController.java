package com.agent.hopaw.controller;

import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.infra.model.dto.ToolSetInfo;
import com.agent.hopaw.infra.model.entity.Agent;
import com.agent.hopaw.infra.model.entity.AiModel;
import com.agent.hopaw.infra.service.AgentService;
import com.agent.hopaw.infra.service.AiModelService;
import com.agent.hopaw.infra.tool.IAgentToolService;
import com.agent.hopaw.util.CurrentUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
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
    public String createAgent(HttpServletRequest request,
                             @RequestParam String name,
                             @RequestParam String description,
                             @RequestParam(required = false) String tools,
                             @RequestParam(required = false, defaultValue = "20") Integer maxMemoryRecords,
                             @RequestParam(required = false, defaultValue = "10") Integer maxToolInvocations,
                             @RequestParam Long aiModelId,
                             @RequestParam(required = false, defaultValue = "true") Boolean enableThinking,
                             @RequestParam(required = false, defaultValue = "true") Boolean vectorToolSearch,
                             @RequestParam(required = false, defaultValue = "5") Integer vectorToolSearchMaxResults,
                             @RequestParam(required = false, defaultValue = "false") Boolean enableAllTools) {
        String toolsStr = tools != null ? tools : "";
        agentService.createAgent(name, description, toolsStr, maxMemoryRecords, maxToolInvocations, aiModelId, enableThinking, vectorToolSearch, vectorToolSearchMaxResults, enableAllTools, CurrentUser.require(request));
        return "redirect:/";
    }


    @PostMapping("/agent/update")
    public String updateAgent(HttpServletRequest request,
                             @RequestParam Long id,
                             @RequestParam String name,
                             @RequestParam String description,
                             @RequestParam(required = false) String tools,
                             @RequestParam(required = false, defaultValue = "20") Integer maxMemoryRecords,
                             @RequestParam(required = false, defaultValue = "10") Integer maxToolInvocations,
                             @RequestParam Long aiModelId,
                             @RequestParam(required = false) Boolean enableThinking,
                             @RequestParam(required = false, defaultValue = "true") Boolean vectorToolSearch,
                             @RequestParam(required = false, defaultValue = "5") Integer vectorToolSearchMaxResults,
                             @RequestParam(required = false, defaultValue = "false") Boolean enableAllTools) {
        String toolsStr = tools != null ? tools : "";
        agentService.updateAgent(CurrentUser.require(request), id, name, description, toolsStr, maxMemoryRecords, maxToolInvocations, aiModelId, enableThinking, vectorToolSearch, vectorToolSearchMaxResults, enableAllTools);
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
    public ResponseBean getAgentsPage(HttpServletRequest request,
                                      @RequestParam(required = false, defaultValue = "") String keyword,
                                      @RequestParam(required = false, defaultValue = "1") int page,
                                      @RequestParam(required = false, defaultValue = "10") int size) {
        String currentUserId = CurrentUser.require(request);
        List<Agent> list = agentService.getAgentsPage(currentUserId, keyword, page, size);
        int total = agentService.countAgents(currentUserId, keyword);
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return ResponseBean.success(result);
    }

    @GetMapping("/api/agents/count")
    @ResponseBody
    public ResponseBean getAgentsCount(HttpServletRequest request) {
        int total = agentService.countAgents(CurrentUser.require(request), null);
        return ResponseBean.success(total);
    }

    @DeleteMapping("/api/agents/{id}")
    @ResponseBody
    public ResponseBean deleteAgent(HttpServletRequest request, @PathVariable Long id) {
        String currentUserId = CurrentUser.require(request);
        int total = agentService.countAgents(currentUserId, null);
        if (total <= 1) {
            return ResponseBean.fail("必须保留至少一个智能体");
        }
        agentService.deleteAgent(id, currentUserId);
        return ResponseBean.success();
    }
}
