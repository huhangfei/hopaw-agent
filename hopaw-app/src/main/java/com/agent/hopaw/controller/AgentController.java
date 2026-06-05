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
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.beans.PropertyEditorSupport;
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

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        // 处理 tools 多选值：将 String[] 转为逗号分隔的字符串
        binder.registerCustomEditor(String.class, "tools", new PropertyEditorSupport() {
            @Override
            public void setValue(Object value) {
                if (value instanceof String[]) {
                    super.setValue(String.join(",", (String[]) value));
                } else {
                    super.setValue(value);
                }
            }
        });
    }

    @PostMapping("/agent/create")
    public String createAgent(HttpServletRequest request, @ModelAttribute Agent agent) {
        agent.setUserId(CurrentUser.require(request));
        agentService.createAgent(agent);
        return "redirect:/";
    }


    @PostMapping("/agent/update")
    public String updateAgent(HttpServletRequest request, @ModelAttribute Agent agent) {
        agentService.updateAgent(agent);
        return "redirect:/?agentId=" + agent.getId();
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
