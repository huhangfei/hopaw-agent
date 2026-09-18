package com.agent.hopaw.infra.model.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 映射组结构配置项：主体声明 mapKey（组名）的配置，每个 mapKey 对应一组子配置值（values）。
 * <p>适用于一个工具管理多组同构配置的场景（如多个公众号、多个数据源）。</p>
 * <p>存储结构（sys_config 表）：主体 configKey 的值为一个 JSON 对象，
 * 形如 {"mapKey1":{"子配置key":"值",...},"mapKey2":{...}}，整体仅一条记录，对象顺序即 mapKey 顺序。</p>
 * <p>约束：mapKey 不能重复，且不能包含英文逗号和冒号；
 * values 仅允许基础类型配置项，不允许嵌套 ToolMapConfigItem。</p>
 */
public class ToolMapConfigItem extends ToolConfigItem {

    /** 每个 mapKey 对应的一组子配置定义（仅基础类型） */
    private List<ToolConfigItem> values = new ArrayList<>();

    public ToolMapConfigItem() {
        super();
        setStructure(ConfigStructure.MAP);
    }

    /**
     * @param key         主体配置键（mapKey 列表以此键存储，散键以此为前缀）
     * @param label       mapKey 输入的显示名（如"账号名称"）
     * @param description mapKey 输入的说明
     * @param type        mapKey 输入类型（一般为 TEXT_SINGLE，也可用 TEXT_PASSWORD 掩码显示）
     */
    public ToolMapConfigItem(String key, String label, String description, ConfigType type) {
        super(key, label, description, type);
        setStructure(ConfigStructure.MAP);
    }

    public List<ToolConfigItem> getValues() {
        return values;
    }

    public void setValues(List<ToolConfigItem> values) {
        if (values == null) {
            this.values = new ArrayList<>();
            return;
        }
        for (ToolConfigItem item : values) {
            if (item instanceof ToolMapConfigItem) {
                throw new IllegalArgumentException("ToolMapConfigItem 的 values 不允许嵌套 ToolMapConfigItem：" + item.getKey());
            }
        }
        this.values = values;
    }
}
