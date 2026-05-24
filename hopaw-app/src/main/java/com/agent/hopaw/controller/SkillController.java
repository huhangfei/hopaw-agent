package com.agent.hopaw.controller;

import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.infra.model.dto.SkillInfo;
import com.agent.hopaw.infra.service.SkillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/skills")
public class SkillController {

    private static final Logger log = LoggerFactory.getLogger(SkillController.class);

    private final SkillService skillService;

    public SkillController(SkillService skillService) {
        this.skillService = skillService;
    }

    @GetMapping
    public String skillsPage(Model model) {
        model.addAttribute("activePage", "skills");
        return "skills";
    }

    @GetMapping("/api/list")
    @ResponseBody
    public ResponseBean listSkills() {
        List<SkillInfo> skills = skillService.listSkills();
        return ResponseBean.success(skills);
    }

    @GetMapping("/api/{folderName}")
    @ResponseBody
    public ResponseBean getSkill(@PathVariable String folderName) {
        SkillInfo skill = skillService.getSkill(folderName);
        if (skill == null) {
            return ResponseBean.fail("技能不存在: " + folderName);
        }
        return ResponseBean.success(skill);
    }

    @PostMapping("/api")
    @ResponseBody
    public ResponseBean createSkill(@RequestBody SkillInfo skillInfo) {
        try {
            SkillInfo created = skillService.createSkill(skillInfo);
            return ResponseBean.success(created);
        } catch (Exception e) {
            log.error("Failed to create skill", e);
            return ResponseBean.fail(e.getMessage());
        }
    }

    @PutMapping("/api/{folderName}")
    @ResponseBody
    public ResponseBean updateSkill(@PathVariable String folderName, @RequestBody SkillInfo skillInfo) {
        try {
            SkillInfo updated = skillService.updateSkill(folderName, skillInfo);
            return ResponseBean.success(updated);
        } catch (Exception e) {
            log.error("Failed to update skill: {}", folderName, e);
            return ResponseBean.fail(e.getMessage());
        }
    }

    @DeleteMapping("/api/{folderName}")
    @ResponseBody
    public ResponseBean deleteSkill(@PathVariable String folderName) {
        try {
            skillService.deleteSkill(folderName);
            return ResponseBean.success();
        } catch (Exception e) {
            log.error("Failed to delete skill: {}", folderName, e);
            return ResponseBean.fail(e.getMessage());
        }
    }
}