package com.agent.hopaw.infra.service;

import java.util.concurrent.CompletableFuture;

/**
 * 工具执行结果暂存服务（纯 string 透传），供插件 @Tool 在「需要从浏览器前端获取数据」时使用。
 *
 * <p>闭环：</p>
 * <ol>
 *   <li>后端 @Tool 调用 {@link #register(String, String)} 注册槽位；</li>
 *   <li>通过 {@link IWebSocketBridgeService#sendPluginCommand} 下发指令给前端；</li>
 *   <li>前端完成后调上报接口填充结果；</li>
 *   <li>后端 {@link #await(String, long)} 阻塞等待，超时或成功拿到 string 返回。</li>
 * </ol>
 *
 * <p>本服务只做 string 透传与等待，语义由具体 @Tool 决定。</p>
 */
public interface IPluginResultStore {

    /** 默认等待超时（秒）。 */
    long DEFAULT_TIMEOUT_SECONDS = 30L;

    /**
     * 注册待回传槽位。
     *
     * @param requestId 请求唯一标识（后端 @Tool 生成）
     * @param userId    用户标识（可空，为空时不校验用户，仅靠 requestId 唯一性兜底）
     * @return 等待结果的 future
     */
    CompletableFuture<String> register(String requestId, String userId);

    /**
     * 前端上报结果，解除阻塞。
     */
    boolean complete(String requestId, String userId, String value);

    /**
     * 阻塞等待结果，超时返回 null。
     */
    String await(String requestId, long timeoutSec);

    /**
     * 取消并移除槽位。
     */
    void cancel(String requestId);
}
