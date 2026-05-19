package com.agent.hopaw.biz.tool.sysconfig;

import com.agent.hopaw.infra.model.entity.SysConfig;
import com.agent.hopaw.infra.service.SysConfigService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import com.agent.hopaw.infra.tool.AgentTool;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.List;

@Component("sysConfigTool")
public class SysConfigTool implements AgentTool {
    private final SysConfigService sysConfigService;

    public SysConfigTool(SysConfigService sysConfigService) {
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

    @Tool("根据 Key 查询系统配置项的值")
    public String querySystemConfigValue(@P(description = "配置项的 Key") String key) {
        SysConfig config = sysConfigService.getByKey(key);
        if (config == null) {
            return "未找到配置项：" + key;
        }
        return config.getConfigValue();
    }

    @Tool("查询所有系统配置项的 Key、Value 和描述")
    public String queryAllSystemConfigs() {
        List<SysConfig> configs = sysConfigService.getAll();
        if (configs.isEmpty()) {
            return "暂无系统配置项";
        }
        StringBuilder sb = new StringBuilder("系统配置项列表：\n\n");
        for (SysConfig config : configs) {
            sb.append("Key: ").append(config.getConfigKey()).append("\n");
            sb.append("Value: ").append(config.getConfigValue()).append("\n");
            sb.append("描述: ").append(config.getDescription() != null ? config.getDescription() : "").append("\n");
            sb.append("---\n");
        }
        return sb.toString();
    }

    @Tool("保存系统配置项（可能影响系统运行，请谨慎操作）")
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
