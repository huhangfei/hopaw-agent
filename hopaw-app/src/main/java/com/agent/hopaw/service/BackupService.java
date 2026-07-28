package com.agent.hopaw.service;

import com.agent.hopaw.avatar.entity.AgentAvatarConfig;
import com.agent.hopaw.avatar.mapper.AvatarConfigMapper;
import com.agent.hopaw.infra.mapper.AiModelMapper;
import com.agent.hopaw.infra.mapper.AiModelProviderMapper;
import com.agent.hopaw.infra.mapper.AgentMapper;
import com.agent.hopaw.infra.mapper.SysConfigMapper;
import com.agent.hopaw.infra.model.entity.AiModel;
import com.agent.hopaw.infra.model.entity.AiModelProvider;
import com.agent.hopaw.infra.model.entity.Agent;
import com.agent.hopaw.infra.model.entity.SysConfig;
import com.agent.hopaw.infra.util.AesEncryptionUtil;
import com.alibaba.fastjson2.JSON;
import net.lingala.zip4j.io.outputstream.ZipOutputStream;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.EncryptionMethod;
import net.lingala.zip4j.model.enums.CompressionMethod;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.ZipFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);

    private final SysConfigMapper sysConfigMapper;
    private final AiModelProviderMapper aiModelProviderMapper;
    private final AiModelMapper aiModelMapper;
    private final AgentMapper agentMapper;
    private final AvatarConfigMapper avatarConfigMapper;

    public BackupService(SysConfigMapper sysConfigMapper,
                         AiModelProviderMapper aiModelProviderMapper,
                         AiModelMapper aiModelMapper,
                         AgentMapper agentMapper,
                         AvatarConfigMapper avatarConfigMapper) {
        this.sysConfigMapper = sysConfigMapper;
        this.aiModelProviderMapper = aiModelProviderMapper;
        this.aiModelMapper = aiModelMapper;
        this.agentMapper = agentMapper;
        this.avatarConfigMapper = avatarConfigMapper;
    }

    /**
     * 执行备份，生成 zip 文件并返回文件路径。
     * 若 password 非空，则使用 AES-256 加密 zip；否则为标准无密码 zip。
     */
    public Path backup(boolean exportSysConfig, boolean exportModelConfig, boolean exportAgentConfig,
                      String password) throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();

        if (exportSysConfig) {
            List<SysConfig> configs = sysConfigMapper.findAll();
            // 解密敏感字段，导出明文（不导出加密密钥）
            for (SysConfig config : configs) {
                config.setConfigValue(AesEncryptionUtil.decrypt(config.getConfigValue()));
            }
            files.put("sys_config.json", toJsonBytes(configs));
        }

        if (exportModelConfig) {
            List<AiModelProvider> providers = aiModelProviderMapper.findAll();
            // 解密 apiKey，导出明文
            for (AiModelProvider provider : providers) {
                provider.setApiKey(AesEncryptionUtil.decrypt(provider.getApiKey()));
            }
            files.put("ai_model_providers.json", toJsonBytes(providers));
            List<AiModel> models = aiModelMapper.findAll();
            files.put("ai_models.json", toJsonBytes(models));
        }

        if (exportAgentConfig) {
            List<Agent> agents = agentMapper.findAll();
            files.put("agents.json", toJsonBytes(agents));
            List<AgentAvatarConfig> avatarConfigs = avatarConfigMapper.findAll();
            files.put("agent_avatar_config.json", toJsonBytes(avatarConfigs));
        }

        // 生成 zip 文件到临时目录
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path tempDir = Files.createTempDirectory("hopaw-backup-");
        Path zipPath = tempDir.resolve("hopaw-backup-" + timestamp + ".zip");

        boolean usePassword = password != null && !password.isEmpty();
        ZipParameters params = new ZipParameters();
        params.setCompressionMethod(CompressionMethod.DEFLATE);
        params.setCompressionLevel(CompressionLevel.NORMAL);
        if (usePassword) {
            params.setEncryptFiles(true);
            params.setEncryptionMethod(EncryptionMethod.AES);
            params.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);
        }

        // 无密码时使用无密码构造函数，避免传入 null char[]
        try (ZipOutputStream zos = usePassword
                ? new ZipOutputStream(Files.newOutputStream(zipPath), password.toCharArray())
                : new ZipOutputStream(Files.newOutputStream(zipPath))) {
            for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                params.setFileNameInZip(entry.getKey());
                zos.putNextEntry(params);
                try (ByteArrayInputStream in = new ByteArrayInputStream(entry.getValue())) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        zos.write(buffer, 0, len);
                    }
                }
                zos.closeEntry();
            }
        }

        return zipPath;
    }

    private byte[] toJsonBytes(Object obj) {
        String json = JSON.toJSONString(obj);
        return json.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 从备份 zip 文件导入数据。zip 可选加密，password 为空表示无密码。
     * 导入语义：按主键 id upsert（存在则更新，不存在则插入）。
     * 加密字段（apiKey、敏感 SysConfig）导入时使用本机 encryption.key 重新加密。
     *
     * @return 导入结果摘要
     */
    public String restore(File zipFile, String password) throws Exception {
        // 解压到临时目录
        Path tempDir = Files.createTempDirectory("hopaw-restore-");
        try {
            ZipFile zip = new ZipFile(zipFile);
            if (zip.isEncrypted()) {
                if (password == null || password.isEmpty()) {
                    throw new IllegalArgumentException("备份文件已加密，请输入解压密码");
                }
                zip.setPassword(password.toCharArray());
            }
            zip.extractAll(tempDir.toString());

            StringBuilder summary = new StringBuilder();
            int total = 0;

            // sys_config.json
            Path sysConfigFile = tempDir.resolve("sys_config.json");
            if (Files.exists(sysConfigFile)) {
                int n = importSysConfig(sysConfigFile);
                total += n;
                summary.append("sys_config: ").append(n).append(" 条\n");
            }

            // ai_model_providers.json + ai_models.json
            Path providerFile = tempDir.resolve("ai_model_providers.json");
            Path modelFile = tempDir.resolve("ai_models.json");
            if (Files.exists(providerFile)) {
                int n = importAiModelProviders(providerFile);
                total += n;
                summary.append("ai_model_providers: ").append(n).append(" 条\n");
            }
            if (Files.exists(modelFile)) {
                int n = importAiModels(modelFile);
                total += n;
                summary.append("ai_models: ").append(n).append(" 条\n");
            }

            // agents.json + agent_avatar_config.json
            Path agentFile = tempDir.resolve("agents.json");
            Path avatarFile = tempDir.resolve("agent_avatar_config.json");
            if (Files.exists(agentFile)) {
                int n = importAgents(agentFile);
                total += n;
                summary.append("agents: ").append(n).append(" 条\n");
            }
            if (Files.exists(avatarFile)) {
                int n = importAvatarConfigs(avatarFile);
                total += n;
                summary.append("agent_avatar_config: ").append(n).append(" 条\n");
            }

            summary.insert(0, "导入完成，共 " + total + " 条记录\n");
            return summary.toString();
        } finally {
            // 清理临时目录
            deleteRecursive(tempDir.toFile());
        }
    }

    private int importSysConfig(Path file) throws Exception {
        String json = Files.readString(file, StandardCharsets.UTF_8);
        List<SysConfig> configs = JSON.parseArray(json, SysConfig.class);
        int count = 0;
        for (SysConfig config : configs) {
            // 备份导出时已解密为明文，导入时用本机密钥重新加密
            config.setConfigValue(AesEncryptionUtil.encrypt(config.getConfigValue()));
            SysConfig existing = config.getId() != null ? findSysConfigById(config.getId()) : null;
            if (existing == null && config.getConfigKey() != null) {
                existing = sysConfigMapper.findByKey(config.getConfigKey());
            }
            if (existing != null) {
                config.setId(existing.getId());
                sysConfigMapper.update(config);
            } else {
                sysConfigMapper.insert(config);
            }
            count++;
        }
        return count;
    }

    private SysConfig findSysConfigById(Long id) {
        // SysConfigMapper 缺少 findById，通过 findAll 查找
        for (SysConfig c : sysConfigMapper.findAll()) {
            if (id.equals(c.getId())) {
                return c;
            }
        }
        return null;
    }

    private int importAiModelProviders(Path file) throws Exception {
        String json = Files.readString(file, StandardCharsets.UTF_8);
        List<AiModelProvider> providers = JSON.parseArray(json, AiModelProvider.class);
        int count = 0;
        for (AiModelProvider provider : providers) {
            // 备份导出时 apiKey 已解密为明文，导入时重新加密
            provider.setApiKey(AesEncryptionUtil.encrypt(provider.getApiKey()));
            AiModelProvider existing = provider.getId() != null ? aiModelProviderMapper.findById(provider.getId()) : null;
            if (existing != null) {
                aiModelProviderMapper.update(provider);
            } else {
                aiModelProviderMapper.insert(provider);
            }
            count++;
        }
        return count;
    }

    private int importAiModels(Path file) throws Exception {
        String json = Files.readString(file, StandardCharsets.UTF_8);
        List<AiModel> models = JSON.parseArray(json, AiModel.class);
        int count = 0;
        for (AiModel model : models) {
            AiModel existing = model.getId() != null ? aiModelMapper.findById(model.getId()) : null;
            if (existing != null) {
                aiModelMapper.update(model);
            } else {
                aiModelMapper.insert(model);
            }
            count++;
        }
        return count;
    }

    private int importAgents(Path file) throws Exception {
        String json = Files.readString(file, StandardCharsets.UTF_8);
        List<Agent> agents = JSON.parseArray(json, Agent.class);
        int count = 0;
        for (Agent agent : agents) {
            Agent existing = agent.getId() != null ? agentMapper.findById(agent.getId()) : null;
            if (existing != null) {
                agentMapper.update(agent);
            } else {
                agentMapper.insert(agent);
            }
            count++;
        }
        return count;
    }

    private int importAvatarConfigs(Path file) throws Exception {
        String json = Files.readString(file, StandardCharsets.UTF_8);
        List<AgentAvatarConfig> configs = JSON.parseArray(json, AgentAvatarConfig.class);
        int count = 0;
        for (AgentAvatarConfig config : configs) {
            // AvatarConfigMapper 缺少 findById，使用 upsert（按 user+agent 复合键）
            if (config.getUserId() != null && config.getAgentId() != null) {
                AgentAvatarConfig existing = avatarConfigMapper.findByUserAndAgent(
                        config.getUserId(), config.getAgentId());
                if (existing != null) {
                    config.setId(existing.getId());
                    avatarConfigMapper.update(config);
                } else {
                    avatarConfigMapper.insert(config);
                }
            } else {
                avatarConfigMapper.insert(config);
            }
            count++;
        }
        return count;
    }

    private static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File c : children) {
                    deleteRecursive(c);
                }
            }
        }
        f.delete();
    }
}
