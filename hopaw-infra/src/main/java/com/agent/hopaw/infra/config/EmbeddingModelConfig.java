package com.agent.hopaw.infra.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzhv15.BgeSmallZhV15EmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Embedding 模型配置
 * 使用单例模式共享模型实例，避免重复加载 ONNX 文件
 * 限制线程数避免 CPU 资源耗尽
 */
@Configuration
public class EmbeddingModelConfig {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddingModelConfig.class);

    /**
     * 嵌入模型线程池大小
     * 建议值：CPU 核心数的一半，避免与其他任务竞争
     */
    private static final int EMBEDDING_THREAD_POOL_SIZE = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);

    @Bean
    public EmbeddingModel embeddingModel() {
        logger.info("Initializing BgeSmallZhV15 Embedding Model with {} threads", EMBEDDING_THREAD_POOL_SIZE);

        // 创建固定大小的线程池，限制并发度
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setName("embedding-worker-" + counter.incrementAndGet());
                thread.setDaemon(true); // 守护线程，不阻止 JVM 退出
                return thread;
            }
        };

        // 使用 ThreadPoolExecutor 手动创建，更好地控制线程池行为
        // 核心线程数 = 最大线程数，固定大小线程池
        // 使用有界队列防止内存溢出，队列大小为线程数的2倍
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                EMBEDDING_THREAD_POOL_SIZE,  // corePoolSize
                EMBEDDING_THREAD_POOL_SIZE,  // maximumPoolSize
                60L,                         // keepAliveTime（固定线程池不适用）
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(EMBEDDING_THREAD_POOL_SIZE * 2),  // 有界队列
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy()  // 队列满时由调用线程执行，提供背压
        );

        // 使用普通版本（如需量化版本节省内存，可替换为 BgeSmallZhV15QuantizedEmbeddingModel）
        // 量化版本依赖：langchain4j-embeddings-bge-small-zh-v15-q
        EmbeddingModel model = new BgeSmallZhV15EmbeddingModel(executor);

        logger.info("BgeSmallZhV15 Embedding Model initialized successfully, threadPoolSize={}", EMBEDDING_THREAD_POOL_SIZE);

        return model;
    }
}
