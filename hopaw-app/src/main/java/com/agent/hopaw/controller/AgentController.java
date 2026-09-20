package com.agent.hopaw.controller;

import com.agent.hopaw.infra.constant.ReasoningEffortEnum;
import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.infra.model.dto.ToolSetInfo;
import com.agent.hopaw.infra.model.entity.Agent;
import com.agent.hopaw.infra.model.entity.AiModel;
import com.agent.hopaw.infra.service.AgentService;
import com.agent.hopaw.infra.service.AiModelService;
import com.agent.hopaw.infra.service.IToolSetService;
import com.agent.hopaw.util.CurrentUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.beans.PropertyEditorSupport;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
public class AgentController {

    private final AgentService agentService;
    private final IToolSetService agentToolService;
    private final AiModelService aiModelService;

    public AgentController(AgentService agentService, IToolSetService agentToolService,
                           AiModelService aiModelService) {
        this.agentService = agentService;
        this.agentToolService = agentToolService;
        this.aiModelService = aiModelService;
    }

    @GetMapping("/agents")
    public String index(Model model) {
        model.addAttribute("activePage", "agents");
        model.addAttribute("activeTab", "agents");
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
        model.addAttribute("reasoningEfforts", ReasoningEffortEnum.values());
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
        model.addAttribute("reasoningEfforts", ReasoningEffortEnum.values());
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
        try {
            int total = agentService.countAgents(currentUserId, null);
            if (total <= 1) {
                return ResponseBean.fail("必须保留至少一个智能体");
            }
            agentService.deleteAgent(id, currentUserId);
            return ResponseBean.success();
        } catch (Exception e) {
            return ResponseBean.fail(e.getMessage());
        }
    }

    @PostMapping("/api/agent/avatar")
    @ResponseBody
    public ResponseBean uploadAvatar(@RequestParam(value = "file", required = false) MultipartFile file,
                                     @RequestParam("agentId") Long agentId,
                                     @RequestParam(value = "clear", defaultValue = "false") boolean clear) {
        Agent agent = agentService.getAgentById(agentId);
        if (agent == null) {
            return ResponseBean.fail("智能体不存在");
        }
        if (clear) {
            agent.setAvatar(null);
            agentService.updateAgent(agent);
            return ResponseBean.success("");
        }
        if (file == null || file.isEmpty()) {
            return ResponseBean.fail("请选择文件");
        }
        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf(".")).toLowerCase();
        }
        if (!".jpg".equals(ext) && !".jpeg".equals(ext) && !".png".equals(ext) && !".gif".equals(ext) && !".webp".equals(ext)) {
            return ResponseBean.fail("仅支持 jpg/jpeg/png/gif/webp 格式");
        }
        String fileName = "agent_" + agentId + "_" + UUID.randomUUID().toString().replace("-", "") + ext;
        File avatarDir = new File(System.getProperty("user.dir"), "avatars");
        if (!avatarDir.exists()) {
            avatarDir.mkdirs();
        }
        File dest = new File(avatarDir, fileName);
        try {
            file.transferTo(dest);
        } catch (IOException e) {
            return ResponseBean.fail("上传失败: " + e.getMessage());
        }
        String avatarUrl = "/avatars/" + fileName;
        agent.setAvatar(avatarUrl);
        agentService.updateAgent(agent);
        return ResponseBean.success(avatarUrl);
    }
}
