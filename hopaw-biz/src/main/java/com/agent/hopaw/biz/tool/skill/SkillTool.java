package com.agent.hopaw.biz.tool.skill;

import com.agent.hopaw.infra.model.dto.SkillInfo;
import com.agent.hopaw.infra.service.ISkillService;
import com.agent.hopaw.infra.tool.ToolSecurityLevel;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.SearchBehavior;
import dev.langchain4j.agent.tool.Tool;
import com.agent.hopaw.infra.tool.AgentTool;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author hhf
 */
@Component("skillTool")
public class SkillTool implements AgentTool {

    private final ISkillService skillService;

    public SkillTool(ISkillService skillService) {
        this.skillService = skillService;
    }

    @Override
    public String getName() {
        return "skillTool";
    }

    @Override
    public String getDescription() {
        return "技能查询工具，支持查询技能列表和技能详情";
    }

    @Override
    public String getKeyword() {
        return "技能";
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = {"列出技能", "查询所有技能列表，仅返回每个技能的 名称、Slug 和 描述"})
    public String listSkills() {
        List<SkillInfo> skills = skillService.listSkills();
        if (skills == null || skills.isEmpty()) {
            return "暂无技能";
        }
        StringBuilder sb = new StringBuilder("技能列表（共 ").append(skills.size()).append(" 个）：\n\n");
        int index = 1;
        for (SkillInfo skill : skills) {
            sb.append("[").append(index++).append("]\n");
            sb.append("名称: ").append(skill.getName() != null ? skill.getName() : "").append("\n");
            sb.append("Slug: ").append(skill.getSlug() != null ? skill.getSlug() : skill.getFolderName() != null ? skill.getFolderName() : "").append("\n");
            sb.append("描述: ").append(skill.getDescription() != null ? skill.getDescription() : "").append("\n");
            sb.append("---\n");
        }
        return sb.toString();
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = {"通过Slug获取技能", "根据 Slug 查询技能的具体内容，返回技能的名称、版本、描述、主页和完整内容"})
    public String getSkillBySlug(@P(description = "技能的 Slug 标识") String slug) {
        SkillInfo found = skillService.getSkill(slug);

        if (found == null) {
            List<SkillInfo> skills = skillService.listSkills();
            if (skills != null) {
                found = skills.stream()
                        .filter(s -> slug.equals(s.getSlug()) || slug.equals(s.getFolderName()))
                        .findFirst()
                        .orElse(null);
            }
        }

        if (found == null) {
            return "未找到技能：" + slug;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("技能名称: ").append(found.getName() != null ? found.getName() : "").append("\n");
        sb.append("Slug: ").append(found.getSlug() != null ? found.getSlug() : found.getFolderName() != null ? found.getFolderName() : "").append("\n");
        sb.append("版本: ").append(found.getVersion() != null ? found.getVersion() : "").append("\n");
        sb.append("描述: ").append(found.getDescription() != null ? found.getDescription() : "").append("\n");
        sb.append("主页: ").append(found.getHomepage() != null ? found.getHomepage() : "").append("\n");
        sb.append("---\n");
        sb.append("内容:\n").append(found.getContent() != null ? found.getContent() : "");
        return sb.toString();
    }
}