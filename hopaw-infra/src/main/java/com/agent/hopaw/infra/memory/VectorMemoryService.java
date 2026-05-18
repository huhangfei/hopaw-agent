package com.agent.hopaw.infra.memory;

import com.agent.hopaw.infra.constant.VectorMemoryTypeEnum;
import com.agent.hopaw.infra.service.SysConfigService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.community.store.embedding.jvector.JVectorEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class VectorMemoryService {

    private static final Logger logger = LoggerFactory.getLogger(VectorMemoryService.class);

    private static final String METADATA_AGENT_ID = "agentId";
    private static final String METADATA_USER_ID = "userId";
    private static final String METADATA_MEMORY_TYPE = "memoryType";
    private static final String METADATA_MEMORY_ID = "memoryId";

    private final SysConfigService sysConfigService;
    private final EmbeddingModel embeddingModel;
    private EmbeddingStore<TextSegment> embeddingStore;
    private String currentPersistencePath;

    public VectorMemoryService(SysConfigService sysConfigService, EmbeddingModel embeddingModel) {
        this.sysConfigService = sysConfigService;
        this.embeddingModel = embeddingModel;
    }

    @PostConstruct
    public void init() {
        try {
            int dimension = 512;

            String persistencePath = sysConfigService.getValueByKey("vector_store_path", "./vector_store");
            String profile = sysConfigService.getValueByKey("vector_store_profile", "stable");

            /*
             * JVector 基于融合 DiskANN + HNSW 算法的纯 Java 向量检索引擎。
             * 核心原理：构建分层可导航小世界图 (HNSW)，
             * 高层稀疏快速跳过不相关区域，底层密集精确定位近邻。
             *
             * ====== 三档预设 (通过 vector_store_profile 配置切换) ======
             *
             * 【precision - 精准模式】适用场景：对召回率要求极高，可接受较长构建时间。
             *   maxDegree=32  beamWidth=300  neighborOverflow=1.5  alpha=1.5
             *   特点：高连接度 + 高构建质量 + 高多样性 → 召回率最优
             *   代价：内存 ~2x，构建耗时 ~3x
             *
             * 【stable - 稳定模式】(默认) 适用场景：十万~百万级向量，均衡精度与性能。
             *   maxDegree=16  beamWidth=100  neighborOverflow=1.2  alpha=1.2
             *   特点：标准参数，业界推荐默认值
             *
             * 【speed - 极速模式】适用场景：数据量小或对构建速度和内存敏感。
             *   maxDegree=8   beamWidth=50   neighborOverflow=1.0  alpha=1.0
             *   特点：低连接度 + 快速构建 + 省内存 → 构建/加载最快
             *   代价：召回率下降约 5~15%
             *
             * ====== 参数含义 ======
             * maxDegree        : 每节点最大边数，影响召回率与内存
             * beamWidth        : 构建束宽，影响索引质量（不影响在线查询）
             * neighborOverflow : 候选邻居溢出系数，1.2=内存优先, 1.5=磁盘优先
             * alpha            : 边多样性 (DiskANN)，1.2=高维推荐，2.0=低维推荐
             */

            int maxDegree, beamWidth;
            float neighborOverflow, alpha;

            switch (profile.toLowerCase()) {
                case "precision":
                    maxDegree = 32; beamWidth = 300; neighborOverflow = 1.5f; alpha = 1.5f; break;
                case "speed":
                    maxDegree = 8;  beamWidth = 50;  neighborOverflow = 1.0f; alpha = 1.0f; break;
                case "stable": default:
                    maxDegree = 16; beamWidth = 100; neighborOverflow = 1.2f; alpha = 1.2f; break;
            }

            embeddingStore = (JVectorEmbeddingStore) JVectorEmbeddingStore.builder()
                    .dimension(dimension)
                    .persistencePath(persistencePath)
                    .maxDegree(maxDegree)
                    .beamWidth(beamWidth)
                    .neighborOverflow(neighborOverflow)
                    .alpha(alpha)
                    .build();

            logger.info("JVectorEmbeddingStore initialized, profile={}, dimension={}, maxDegree={}, beamWidth={}, path={}",
                    profile, dimension, maxDegree, beamWidth, persistencePath);
            currentPersistencePath = persistencePath;
        } catch (Exception e) {
            logger.error("Failed to initialize JVectorEmbeddingStore", e);
            throw new RuntimeException("Vector store initialization failed", e);
        }
    }

    @PreDestroy
    public void destroy() {
        try {
            if (embeddingStore instanceof JVectorEmbeddingStore) {
                ((JVectorEmbeddingStore) embeddingStore).save();
                logger.info("JVectorEmbeddingStore saved on shutdown");
            }
        } catch (Exception e) {
            logger.error("Failed to save vector store on shutdown", e);
        }
    }

    /**
     * 重建向量引擎。
     *
     * 完整流程：
     *   ① save()                   - 旧数据刷入磁盘（保存在旧路径）
     *   ② 读取配置中的新路径
     *   ③ 对比新旧路径
     *        ├─ 相同 → 跳过迁移
     *        └─ 不同 → migrateStoreFiles() 将旧目录文件复制到新目录
     *   ④ embeddingStore = null
     *   ⑤ init()                   - 用新路径创建 store，JVector 自动加载持久化文件
     *
     * 注意：图构建参数 (maxDegree/beamWidth 等) 只对新插入的向量生效，
     * 已持久化的向量加载后仍保留原有的图结构。
     * 如需彻底切换预设参数，需手动删除持久化目录后重建。
     */
    public synchronized void reinit() {
        logger.info("Rebuilding JVectorEmbeddingStore...");

        String newPath = sysConfigService.getValueByKey("vector_store_path", "./vector_store");

        try {
            if (embeddingStore instanceof JVectorEmbeddingStore) {
                ((JVectorEmbeddingStore) embeddingStore).save();
            }
        } catch (Exception e) {
            logger.warn("Failed to save vector store before rebuild", e);
        }

        boolean pathChanged = currentPersistencePath != null
                && !currentPersistencePath.equals(newPath);

        if (pathChanged) {
            migrateStoreFiles(currentPersistencePath, newPath);
        }

        embeddingStore = null;
        init();
        logger.info("JVectorEmbeddingStore rebuilt successfully, pathChanged={}", pathChanged);
    }

    /**
     * 将旧路径下的向量存储文件迁移到新路径。
     * 使用 Files.copy 而非 move，保证原文件不受影响，迁移失败也可回退。
     */
    private void migrateStoreFiles(String oldPath, String newPath) {
        try {
            Path oldDir = Paths.get(oldPath);
            Path newDir = Paths.get(newPath);
            if (!Files.exists(oldDir) || !Files.isDirectory(oldDir)) {
                logger.info("Old persistence path does not exist, skip migration: {}", oldPath);
                return;
            }
            Files.createDirectories(newDir);
            Files.list(oldDir).forEach(file -> {
                try {
                    Path target = newDir.resolve(file.getFileName());
                    Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                    logger.debug("Migrated vector store file: {} -> {}", file, target);
                } catch (Exception e) {
                    logger.error("Failed to migrate file: {}", file, e);
                }
            });
            logger.info("Vector store files migrated from '{}' to '{}'", oldPath, newPath);
        } catch (Exception e) {
            logger.error("Failed to migrate vector store files from '{}' to '{}'",
                    oldPath, newPath, e);
        }
    }

    /**
     * 将内容写入向量库，附带 agent、用户、记忆类型、记忆ID 等分类信息
     */
    public void store(String content, Long agentId, String userId, VectorMemoryTypeEnum memoryType, Long memoryId) {
        if (content == null || content.isBlank()) {
            return;
        }
        try {
            TextSegment segment = TextSegment.from(content);
            segment.metadata().put(METADATA_AGENT_ID, String.valueOf(agentId));
            segment.metadata().put(METADATA_USER_ID, userId);
            segment.metadata().put(METADATA_MEMORY_TYPE, memoryType.getCode());
            if (memoryId != null) {
                segment.metadata().put(METADATA_MEMORY_ID, String.valueOf(memoryId));
            }

            Embedding embedding = embeddingModel.embed(segment).content();
            embeddingStore.add(embedding, segment);

            if (embeddingStore instanceof JVectorEmbeddingStore) {
                ((JVectorEmbeddingStore) embeddingStore).save();
            }
        } catch (Exception e) {
            logger.error("Failed to store vector memory, agentId={}, userId={}, type={}",
                    agentId, userId, memoryType, e);
        }
    }

    /**
     * 批量写入向量库
     */
    public void storeBatch(List<String> contents, Long agentId, String userId, VectorMemoryTypeEnum memoryType, List<Long> memoryIds) {
        if (contents == null || contents.isEmpty()) {
            return;
        }
        try {
            List<TextSegment> segments = new java.util.ArrayList<>();
            for (int i = 0; i < contents.size(); i++) {
                String content = contents.get(i);
                if (content == null || content.isBlank()) {
                    continue;
                }
                TextSegment segment = TextSegment.from(content);
                segment.metadata().put(METADATA_AGENT_ID, String.valueOf(agentId));
                segment.metadata().put(METADATA_USER_ID, userId);
                segment.metadata().put(METADATA_MEMORY_TYPE, memoryType.getCode());
                if (memoryIds != null && i < memoryIds.size() && memoryIds.get(i) != null) {
                    segment.metadata().put(METADATA_MEMORY_ID, String.valueOf(memoryIds.get(i)));
                }
                segments.add(segment);
            }

            if (segments.isEmpty()) {
                return;
            }

            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
            embeddingStore.addAll(embeddings, segments);

            if (embeddingStore instanceof JVectorEmbeddingStore) {
                ((JVectorEmbeddingStore) embeddingStore).save();
            }

            logger.info("Batch stored {} vectors, agentId={}, userId={}, type={}",
                    segments.size(), agentId, userId, memoryType);
        } catch (Exception e) {
            logger.error("Failed to batch store vectors, agentId={}, userId={}, type={}",
                    agentId, userId, memoryType, e);
        }
    }

    /**
     * 语义检索向量库中的历史记忆
     *
     * @param query     用户查询文本
     * @param agentId   智能体ID（可选过滤），传 null 表示不过滤
     * @param userId    用户ID（可选过滤），传 null 表示不过滤
     * @param memoryType 记忆类型，传 null 表示不过滤
     * @param maxResults 最大返回数
     * @param minScore   最低相似度阈值
     */
    public List<VectorSearchResult> search(String query, Long agentId, String userId,
                                           VectorMemoryTypeEnum memoryType, int maxResults, double minScore) {
        try {
            Embedding queryEmbedding = embeddingModel.embed(TextSegment.from(query)).content();

            int searchLimit = Math.max(maxResults * 5, 20);

            EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(searchLimit)
                    .minScore(minScore)
                    .build();

            EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);

            return result.matches().stream()
                    .filter(match -> {
                        dev.langchain4j.data.document.Metadata metadata = match.embedded().metadata();
                        if (agentId != null) {
                            String storedAgentId = metadata.getString(METADATA_AGENT_ID);
                            if (!String.valueOf(agentId).equals(storedAgentId)) {
                                return false;
                            }
                        }
                        if (userId != null) {
                            String storedUserId = metadata.getString(METADATA_USER_ID);
                            if (!userId.equals(storedUserId)) {
                                return false;
                            }
                        }
                        if (memoryType != null) {
                            String storedType = metadata.getString(METADATA_MEMORY_TYPE);
                            if (!memoryType.getCode().equals(storedType)) {
                                return false;
                            }
                        }
                        return true;
                    })
                    .limit(maxResults)
                    .map(match -> new VectorSearchResult(
                            match.score(),
                            match.embedded().text(),
                            match.embedded().metadata()))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            logger.error("Failed to search vector store, query={}", query, e);
            return List.of();
        }
    }

    /**
     * 向量搜索结果
     */
    public static class VectorSearchResult {
        private final double score;
        private final String text;
        private final dev.langchain4j.data.document.Metadata metadata;

        public VectorSearchResult(double score, String text, dev.langchain4j.data.document.Metadata metadata) {
            this.score = score;
            this.text = text;
            this.metadata = metadata;
        }

        public double getScore() {
            return score;
        }

        public String getText() {
            return text;
        }

        public dev.langchain4j.data.document.Metadata getMetadata() {
            return metadata;
        }

        public String getAgentId() {
            return metadata != null ? metadata.getString(METADATA_AGENT_ID) : null;
        }

        public String getUserId() {
            return metadata != null ? metadata.getString(METADATA_USER_ID) : null;
        }

        public String getMemoryType() {
            return metadata != null ? metadata.getString(METADATA_MEMORY_TYPE) : null;
        }

        @Override
        public String toString() {
            return String.format("[score=%.4f, type=%s, text=%s]",
                    score, getMemoryType(),
                    text != null && text.length() > 100 ? text.substring(0, 100) + "..." : text);
        }
    }
}