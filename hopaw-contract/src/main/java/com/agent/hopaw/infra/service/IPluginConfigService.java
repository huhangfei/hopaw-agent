package com.agent.hopaw.infra.service;

/**
 * 插件级配置读写契约（配置键前缀由框架统一计算为 {@code plugin.<pluginId>.}）。
 *
 * <p>供插件在自身代码中读取/写入插件级配置，替代自行拼接配置 key 的散装读法。
 * 工具级配置仍走 {@code tool.<工具集名>.} 前缀（工具通过 SysConfig + getConfigPrefix 读取）。</p>
 */
public interface IPluginConfigService {

    /**
     * 读取插件级配置项：{@code plugin.<pluginId>.<key>}。
     *
     * @param key          配置项 key（不含前缀）
     * @param defaultValue 未配置时返回的默认值
     */
    String get(String pluginId, String key, String defaultValue);

    /**
     * 写入插件级配置项（明文存储）。
     */
    void put(String pluginId, String key, String value);

    /**
     * 读取任意配置键的原始值（如工具级 {@code tool.<工具集名>.<key>}）。
     */
    String getValue(String configKey, String defaultValue);

    /**
     * 删除任意配置键。
     */
    void delete(String configKey);
}
