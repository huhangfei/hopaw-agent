package com.agent.hopaw.service;

import com.agent.hopaw.infra.service.IPluginResultStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 工具执行结果暂存服务（纯 string 透传），{@link IPluginResultStore} 的实现。
 *
 * <p>用于「后端 @Tool 方法需要从浏览器前端获取数据」的闭环：</p>
 * <ol>
 *   <li>后端 @Tool 调用 {@link #register(String, String)} 注册一个待回传槽位（关联 userId）；</li>
 *   <li>后端通过 WS 下发指令给前端；</li>
 *   <li>前端完成操作后调用上报接口，{@link #complete(String, String, String)} 填充结果；</li>
 *   <li>后端 {@link #await(String, long)} 阻塞等待，超时或成功拿到 string 返回。</li>
 * </ol>
 *
 * <p>本服务只做 string 的透传与等待，不解析、不落盘、不关心语义——存什么、返回给 LLM 什么，
 * 完全由具体 {@code @Tool} 决定（图片 base64 / URL / JSON / 纯文本均可）。</p>
 */
@Service
public class ToolResultStore implements IPluginResultStore {

    private static final Logger logger = LoggerFactory.getLogger(ToolResultStore.class);

    /** 槽位：requestId -> (userId, future)。 */
    private final Map<String, Slot> pending = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<String> register(String requestId, String userId) {
        Slot slot = new Slot(userId, new CompletableFuture<>());
        pending.put(requestId, slot);
        return slot.future;
    }

    @Override
    public boolean complete(String requestId, String userId, String value) {
        Slot slot = pending.get(requestId);
        if (slot == null) {
            return false;
        }
        // userId 为空（后端未传）时不强校验，仅靠 requestId 唯一性兜底
        if (slot.userId != null && userId != null && !slot.userId.equals(userId)) {
            logger.warn("ToolResultStore: userId mismatch for requestId={}, expected={}, got={}",
                    requestId, slot.userId, userId);
            return false;
        }
        slot.future.complete(value == null ? "" : value);
        pending.remove(requestId);
        return true;
    }

    @Override
    public String await(String requestId, long timeoutSec) {
        Slot slot = pending.get(requestId);
        if (slot == null) {
            return null;
        }
        try {
            String value = slot.future.get(timeoutSec, TimeUnit.SECONDS);
            pending.remove(requestId);
            return value;
        } catch (Exception e) {
            pending.remove(requestId);
            logger.warn("ToolResultStore: await timeout/failed for requestId={}: {}", requestId, e.getMessage());
            return null;
        }
    }

    @Override
    public void cancel(String requestId) {
        Slot slot = pending.remove(requestId);
        if (slot != null) {
            slot.future.cancel(false);
        }
    }

    private static class Slot {
        final String userId;
        final CompletableFuture<String> future;

        Slot(String userId, CompletableFuture<String> future) {
            this.userId = userId;
            this.future = future;
        }
    }
}
