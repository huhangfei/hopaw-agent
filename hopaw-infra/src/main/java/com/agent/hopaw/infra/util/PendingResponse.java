package com.agent.hopaw.infra.util;

import java.io.Serializable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 一种可以外部完成{@link DelayedResponse}的实现，
 * 适用于从外部源接收响应的场景，并且工作流必须能在进程重启后恢复。
 *
 * @param <T> 响应值的类型
 */
public class PendingResponse<T> implements DelayedResponse<T>, Serializable {

    private final String responseId;
    private final transient CompletableFuture<T> future;

    /**
     * 使用指定的唯一标识符创建一个新的等待响应。
     *
     * @param responseId 唯一标识符，用于外部系统定位和完成此等待响应
     */
    public PendingResponse(String responseId) {
        this.responseId = responseId;
        this.future = new CompletableFuture<>();
    }

    /**
     * 返回此等待响应的唯一标识符。
     *
     * @return 响应标识符
     */
    public String responseId() {
        return responseId;
    }

    /**
     * 阻塞等待响应，直到响应可用。
     *
     * @return 响应值
     */
    @Override
    public T blockingGet() {
        return future.join();
    }

    /**
     * 在指定超时时间内阻塞等待响应。
     *
     * @param timeout 最大等待时间
     * @param unit    时间单位
     * @return 响应值
     * @throws TimeoutException 如果等待超时
     */
    @Override
    public T blockingGet(long timeout, TimeUnit unit) throws TimeoutException {
        try {
            return future.get(timeout, unit);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new TimeoutException(e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 检查此等待响应是否已完成。
     *
     * @return 如果已完成则返回true
     */
    @Override
    public boolean isDone() {
        return future.isDone();
    }

    /**
     * 使用给定的值完成此等待响应。
     * 所有阻塞在{@link #blockingGet()}上的线程都将被释放。
     *
     * @param value 响应值
     * @return 如果此调用导致响应转变为完成状态则返回true，如果已完成则返回false
     */
    public boolean complete(T value) {
        return future.complete(value);
    }

    /**
     * 异常完成此等待响应。
     *
     * @param exception 异常
     * @return 如果此调用导致响应转变为完成状态则返回true，如果已完成则返回false
     */
    public boolean completeExceptionally(Throwable exception) {
        return future.completeExceptionally(exception);
    }

    // 支持工作流恢复的序列化逻辑 (实现细节可能因框架版本而异)
    private Object writeReplace() {
        return new SerializationProxy<>(this);
    }

    private static class SerializationProxy<T> implements Serializable {
        private final String responseId;

        SerializationProxy(PendingResponse<T> original) {
            this.responseId = original.responseId();
        }

        private Object readResolve() {
            return new PendingResponse<>(responseId);
        }
    }
}

/**
 * 延迟响应接口，定义了获取异步响应结果的核心契约。
 *
 * @param <T> 响应值的类型
 */
interface DelayedResponse<T> {
    T blockingGet();
    T blockingGet(long timeout, TimeUnit unit) throws TimeoutException;
    boolean isDone();
}