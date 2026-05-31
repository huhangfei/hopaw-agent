package com.agent.hopaw.biz.tool.sysconfig;

import com.agent.hopaw.infra.model.dto.ToolConfigItem;
import com.agent.hopaw.infra.model.entity.SysConfig;
import com.agent.hopaw.infra.service.ISysConfigService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.SearchBehavior;
import dev.langchain4j.agent.tool.Tool;
import com.agent.hopaw.infra.tool.AgentTool;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.List;

@Component("sysConfigTool")
public class SysConfigTool implements AgentTool {
    private final ISysConfigService sysConfigService;

    public SysConfigTool(ISysConfigService sysConfigService) {
        this.sysConfigService = sysConfigService;
    }

    @Override
    public String getName() {
        return "sysConfigTool";
    }

    @Override
    public String getDescription() {
        return "修改查询系统配置项（可能影响系统运行，请谨慎操作）";
    }

    @Override
    public String getIcon() {
        return "sys-config-tool.svg";
    }

    @Override
    public String getKeyword() {
        return "配置";
    }

    @Override
    public List<ToolConfigItem> getConfigItems() {
        return List.of(
                new ToolConfigItem("exampleText", "示例文本", "这是一个单文本配置示例", ToolConfigItem.ConfigType.TEXT_SINGLE),
                new ToolConfigItem("exampleSelect", "示例下拉", "从预设选项中选择一个", ToolConfigItem.ConfigType.SELECT, List.of("选项1", "选项2", "选项3")),
                new ToolConfigItem("exampleRadio", "示例单选", "选择一个选项", ToolConfigItem.ConfigType.RADIO, List.of("苹果", "香蕉", "橙子")),
                new ToolConfigItem("exampleCheck", "示例多选", "可以选择多个", ToolConfigItem.ConfigType.CHECKBOX, List.of("A", "B", "C", "D")),
                new ToolConfigItem("exampleMultiText", "示例多文本", "支持多行输入", ToolConfigItem.ConfigType.TEXT_MULTI)
        );
    }

    @Tool(value = "根据 Key 查询系统配置项的值",searchBehavior = SearchBehavior.ALWAYS_VISIBLE)
    public String querySystemConfigValue(@P(description = "配置项的 Key") String key) {
        SysConfig config = sysConfigService.getByKey(key);
        if (config == null) {
            return "未找到配置项：" + key;
        }
        return config.getConfigValue();
    }

    @Tool(value={"查询所有系统配置项的 Key和描述","Value值通过调用querySystemConfigValue接口获取"},searchBehavior = SearchBehavior.ALWAYS_VISIBLE)
    public String queryAllSystemConfigs() {
        List<SysConfig> configs = sysConfigService.getAll();
        if (configs.isEmpty()) {
            return "暂无系统配置项";
        }
        StringBuilder sb = new StringBuilder("系统配置项列表：\n\n");
        for (SysConfig config : configs) {
            sb.append("Key: ").append(config.getConfigKey()).append("\n");
            sb.append("描述: ").append(config.getDescription() != null ? config.getDescription() : "").append("\n");
            sb.append("---\n");
        }
        return sb.toString();
    }

    @Tool(value="保存系统配置项（可能影响系统运行，请谨慎操作）",searchBehavior = SearchBehavior.ALWAYS_VISIBLE)
    public String saveSystemConfig(@P(description = "配置项的 Key") String key,
                             @P(description = "配置项的值") String value,
                             @P(description = "配置项的描述", required = false) String description) {
        SysConfig existing = sysConfigService.getByKey(key);
        if (existing != null) {
            existing.setConfigValue(value);
            if (description != null) {
                existing.setDescription(description);
            }
            sysConfigService.update(existing);
            return "已更新配置项：" + key;
        } else {
            SysConfig newConfig = new SysConfig(key, value, description != null ? description : "");
            sysConfigService.save(newConfig);
            return "已新增配置项：" + key;
        }
    }
}
