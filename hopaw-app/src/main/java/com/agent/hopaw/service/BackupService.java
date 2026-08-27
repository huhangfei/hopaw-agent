package com.agent.hopaw.service;

import com.agent.hopaw.avatar.entity.AgentAvatarConfig;
import com.agent.hopaw.avatar.mapper.AvatarConfigMapper;
import com.agent.hopaw.infra.mapper.AiModelMapper;
import com.agent.hopaw.infra.mapper.AiModelProviderMapper;
import com.agent.hopaw.infra.mapper.AgentMapper;
import com.agent.hopaw.infra.mapper.LongTermMemoryMapper;
import com.agent.hopaw.infra.mapper.SysConfigMapper;
import com.agent.hopaw.infra.mapper.TtsConfigMapper;
import com.agent.hopaw.infra.memory.ILongTermMemoryService;
import com.agent.hopaw.infra.model.entity.AiModel;
import com.agent.hopaw.infra.model.entity.AiModelProvider;
import com.agent.hopaw.infra.model.entity.Agent;
import com.agent.hopaw.infra.model.entity.LongTermMemory;
import com.agent.hopaw.infra.model.entity.SysConfig;
import com.agent.hopaw.infra.model.entity.TtsConfig;
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
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.security.SecureRandom;
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
    private final TtsConfigMapper ttsConfigMapper;
    private final LongTermMemoryMapper longTermMemoryMapper;
    private final ILongTermMemoryService longTermMemoryService;
    private final Path encryptionKeyPath;

    public BackupService(SysConfigMapper sysConfigMapper,
                         AiModelProviderMapper aiModelProviderMapper,
                         AiModelMapper aiModelMapper,
                         AgentMapper agentMapper,
                         AvatarConfigMapper avatarConfigMapper,
                         TtsConfigMapper ttsConfigMapper,
                         LongTermMemoryMapper longTermMemoryMapper,
                         ILongTermMemoryService longTermMemoryService) {
        this.sysConfigMapper = sysConfigMapper;
        this.aiModelProviderMapper = aiModelProviderMapper;
        this.aiModelMapper = aiModelMapper;
        this.agentMapper = agentMapper;
        this.avatarConfigMapper = avatarConfigMapper;
        this.ttsConfigMapper = ttsConfigMapper;
        this.longTermMemoryMapper = longTermMemoryMapper;
        this.longTermMemoryService = longTermMemoryService;
        this.encryptionKeyPath = Paths.get(System.getProperty("user.home"), ".hopaw", "encryption.key");
    }

    /**
     * 备份结果：zip 文件路径 + 后端生成的 zip 解压密码
     */
    public record BackupResult(Path zipPath, String password) {}

    /**
     * 执行备份。后端始终生成 16 位高强度密码对 zip 加密（AES-256），并随结果返回密码。
     * 前端下载文件后必须向用户展示该密码以便导入时输入。
     */
    public BackupResult backup(boolean exportSysConfig, boolean exportModelConfig, boolean exportAgentConfig,
                      boolean exportTtsConfig, boolean exportMemory) throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();

        if (exportSysConfig) {
            List<SysConfig> configs = sysConfigMapper.findAll();
            // 保留数据库中的密文，导入时使用本机密钥（备份包中随附）解密还原
            files.put("sys_config.json", toJsonBytes(configs));
        }

        if (exportModelConfig) {
            List<AiModelProvider> providers = aiModelProviderMapper.findAll();
            // 保留数据库中的密文 apiKey
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

        if (exportTtsConfig) {
            // TTS configJson 字段以明文 JSON 形式存储于数据库（含厂商 apiKey 等凭证），
            // 备份原样导出；导入时也原样写回。密钥包内的 encryption.key 仍用于其他加密字段。
            List<TtsConfig> ttsConfigs = ttsConfigMapper.findAll();
            files.put("tts_config.json", toJsonBytes(ttsConfigs));
        }

        if (exportMemory) {
            // 长时记忆全量导出（所有用户），导入时重新生成向量
            List<LongTermMemory> memories = longTermMemoryMapper.findAll();
            files.put("long_term_memory.json", toJsonBytes(memories));
        }

        // 打包本机加密密钥（~/.hopaw/encryption.key），导入时还原以正确解密加密字段
        if (Files.exists(encryptionKeyPath)) {
            files.put("encryption.key", Files.readAllBytes(encryptionKeyPath));
            log.info("已随备份打包加密密钥: {}", encryptionKeyPath);
        } else {
            log.warn("未找到加密密钥文件: {}，加密字段将无法在导入端解密", encryptionKeyPath);
        }

        // 生成 zip 文件到临时目录
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path tempDir = Files.createTempDirectory("hopaw-backup-");
        Path zipPath = tempDir.resolve("hopaw-backup-" + timestamp + ".zip");

        // 后端生成 16 位高强度密码（仅含易识别字符，避免 0/O/1/l/I 歧义）
        String password = generateStrongPassword(16);
        log.info("已生成备份解压密码");

        ZipParameters params = new ZipParameters();
        params.setCompressionMethod(CompressionMethod.DEFLATE);
        params.setCompressionLevel(CompressionLevel.NORMAL);
        params.setEncryptFiles(true);
        params.setEncryptionMethod(EncryptionMethod.AES);
        params.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath), password.toCharArray())) {
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

        return new BackupResult(zipPath, password);
    }

    private byte[] toJsonBytes(Object obj) {
        String json = JSON.toJSONString(obj);
        return json.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 生成指定长度的密码：从字符池中用 SecureRandom 抽取，避免使用易混淆字符（0/O/1/l/I）。
     * 字符池包含大小写字母 + 数字，组合空间 56^16 ≈ 1.5e28，暴力破解不可行。
     */
    private static final String PASSWORD_CHARS =
            "ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";

    private static String generateStrongPassword(int length) {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(PASSWORD_CHARS.charAt(random.nextInt(PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }

    /**
     * 从备份 zip 文件导入数据。zip 可选加密，password 为空表示无密码。
     * 导入语义：按主键 id upsert（存在则更新，不存在则插入）。
     * 加密字段（apiKey、敏感 SysConfig）用备份包内旧密钥解密后，以本机当前密钥重新加密写入；
     * 本机 encryption.key 不会被覆盖，本机已有数据不受影响。
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
            try {
                zip.extractAll(tempDir.toString());
            } catch (net.lingala.zip4j.exception.ZipException e) {
                if (e.getMessage() != null && e.getMessage().toLowerCase().contains("wrong password")) {
                    throw new IllegalArgumentException("解压密码错误", e);
                }
                throw e;
            }

            StringBuilder summary = new StringBuilder();
            int total = 0;

            // 不覆盖本机密钥：读取备份包内旧密钥仅用于本次导入解密，
            // 历史密文解密后以本机当前密钥重新加密写入，保证与本机数据密钥一致
            ReEncryptStats reStats = new ReEncryptStats();
            Path keyFile = tempDir.resolve("encryption.key");
            AesEncryptionUtil oldKeyUtil = null;
            if (Files.exists(keyFile)) {
                oldKeyUtil = new AesEncryptionUtil(Files.readAllBytes(keyFile));
                summary.append("encryption_key: 使用备份包内密钥解密并以本机密钥重加密（本机密钥不变）\n");
                log.info("使用备份包内密钥解密导入数据，本机密钥保持不变: {}", encryptionKeyPath);
            } else {
                summary.append("encryption_key: 备份包未包含密钥，加密字段按原样导入\n");
                log.warn("备份包未包含 encryption.key，加密字段将按原样导入（仅当与本机密钥一致时可解密）");
            }

            // sys_config.json
            Path sysConfigFile = tempDir.resolve("sys_config.json");
            if (Files.exists(sysConfigFile)) {
                int n = importSysConfig(sysConfigFile, oldKeyUtil, reStats);
                total += n;
                summary.append("sys_config: ").append(n).append(" 条\n");
            }

            // ai_model_providers.json + ai_models.json
            Path providerFile = tempDir.resolve("ai_model_providers.json");
            Path modelFile = tempDir.resolve("ai_models.json");
            if (Files.exists(providerFile)) {
                int n = importAiModelProviders(providerFile, oldKeyUtil, reStats);
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

            // tts_config.json
            Path ttsFile = tempDir.resolve("tts_config.json");
            if (Files.exists(ttsFile)) {
                int n = importTtsConfigs(ttsFile);
                total += n;
                summary.append("tts_config: ").append(n).append(" 条\n");
            }

            // long_term_memory.json（长时记忆，恢复时重新生成向量）
            Path memoryFile = tempDir.resolve("long_term_memory.json");
            if (Files.exists(memoryFile)) {
                int n = importLongTermMemories(memoryFile);
                total += n;
                summary.append("long_term_memory: ").append(n).append(" 条\n");
            }

            if (reStats.reEncrypted > 0 || reStats.keptAsIs > 0 || reStats.failed > 0) {
                summary.append("密文迁移: 重加密 ").append(reStats.reEncrypted)
                        .append(" 条, 原样保留 ").append(reStats.keptAsIs)
                        .append(" 条, 解密失败 ").append(reStats.failed).append(" 条\n");
            }
            summary.insert(0, "导入完成，共 " + total + " 条记录\n");
            return summary.toString();
        } finally {
            // 清理临时目录
            deleteRecursive(tempDir.toFile());
        }
    }

    private int importSysConfig(Path file, AesEncryptionUtil oldKeyUtil, ReEncryptStats stats) throws Exception {
        String json = Files.readString(file, StandardCharsets.UTF_8);
        List<SysConfig> configs = JSON.parseArray(json, SysConfig.class);
        int count = 0;
        for (SysConfig config : configs) {
            // 备份保留原密文：先用备份包内旧密钥解密，再用本机密钥重新加密写入
            config.setConfigValue(reEncryptIfNeeded(config.getConfigValue(), oldKeyUtil, stats));
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

    private int importAiModelProviders(Path file, AesEncryptionUtil oldKeyUtil, ReEncryptStats stats) throws Exception {
        String json = Files.readString(file, StandardCharsets.UTF_8);
        List<AiModelProvider> providers = JSON.parseArray(json, AiModelProvider.class);
        int count = 0;
        for (AiModelProvider provider : providers) {
            // 备份保留原密文 apiKey：先用备份包内旧密钥解密，再用本机密钥重新加密写入
            provider.setApiKey(reEncryptIfNeeded(provider.getApiKey(), oldKeyUtil, stats));
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

    private int importTtsConfigs(Path file) throws Exception {
        String json = Files.readString(file, StandardCharsets.UTF_8);
        List<TtsConfig> configs = JSON.parseArray(json, TtsConfig.class);
        int count = 0;
        for (TtsConfig config : configs) {
            // configJson 数据库里以明文存储，备份导出时未做转换，导入也原样写回
            TtsConfig existing = config.getId() != null ? ttsConfigMapper.findById(config.getId()) : null;
            if (existing != null) {
                ttsConfigMapper.update(config);
            } else {
                ttsConfigMapper.insert(config);
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

    private int importLongTermMemories(Path file) throws Exception {
        String json = Files.readString(file, StandardCharsets.UTF_8);
        List<LongTermMemory> memories = JSON.parseArray(json, LongTermMemory.class);
        if (memories == null || memories.isEmpty()) {
            return 0;
        }
        // 复用长时记忆导入逻辑：重新生成向量并保留 createTime/parentId 关系
        return longTermMemoryService.restoreUserMemories(memories);
    }

    /**
     * 密文迁移统计
     */
    private static class ReEncryptStats {
        int reEncrypted = 0;
        int keptAsIs = 0;
        int failed = 0;
    }

    /**
     * 密文迁移：若值为 {AES} 密文，先用备份包内旧密钥解密，再用本机密钥重新加密。
     * 备份包无密钥或解密失败时按原样返回，不阻塞整体导入。
     */
    private String reEncryptIfNeeded(String value, AesEncryptionUtil oldKeyUtil, ReEncryptStats stats) {
        if (value == null || !AesEncryptionUtil.isEncrypted(value)) {
            return value;
        }
        if (oldKeyUtil == null) {
            stats.keptAsIs++;
            return value;
        }
        try {
            String plain = oldKeyUtil.decryptWith(value);
            stats.reEncrypted++;
            return AesEncryptionUtil.encrypt(plain);
        } catch (Exception e) {
            stats.failed++;
            log.warn("备份密文使用旧密钥解密失败，按原样导入", e);
            return value;
        }
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
