package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.constant.NotifyEventEnum;
import com.agent.hopaw.infra.mapper.NotifyChannelMapper;
import com.agent.hopaw.infra.mapper.ProjectMapper;
import com.agent.hopaw.infra.model.entity.NotifyChannel;
import com.agent.hopaw.infra.model.entity.Project;
import com.agent.hopaw.infra.notify.NotifySender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson2.JSON;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 公共通知服务实现：
 * - 按渠道编号发送（同步执行，返回发送结果，用于渠道测试等场景）
 * - 按项目通知配置发送（异步执行，不影响业务主流程）：校验项目勾选了该通知事项后，向项目绑定的全部启用渠道发送
 */
@Service
public class NotificationService implements INotificationService {
    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    private final NotifyChannelMapper notifyChannelMapper;
    private final ProjectMapper projectMapper;
    /** 按类型索引的发送器（dingtalk/feishu/email/webhook） */
    private final Map<String, NotifySender> senderMap = new HashMap<>();
    /** 通知发送线程池：单线程足够（通知量低），守护线程不阻塞 JVM 退出 */
    private final ExecutorService notifyExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "notify-sender");
        t.setDaemon(true);
        return t;
    });

    public NotificationService(NotifyChannelMapper notifyChannelMapper,
                               ProjectMapper projectMapper,
                               List<NotifySender> senders) {
        this.notifyChannelMapper = notifyChannelMapper;
        this.projectMapper = projectMapper;
        for (NotifySender sender : senders) {
            this.senderMap.put(sender.getType(), sender);
        }
        logger.info("通知服务初始化完成，已注册发送器: {}", senderMap.keySet());
    }

    @Override
    public String sendByChannelId(Long channelId, String title, String content) {
        NotifyChannel channel = notifyChannelMapper.findById(channelId);
        if (channel == null) {
            return "通知渠道不存在: " + channelId;
        }
        if (Boolean.FALSE.equals(channel.getEnabled())) {
            return "通知渠道已停用: " + channel.getName();
        }
        return dispatch(channel, title, content);
    }

    @Override
    public void sendForProject(Long projectId, String eventCode, String title, String content) {
        if (projectId == null || eventCode == null) {
            return;
        }
        // 同步捕获并校验项目通知配置快照，再异步发送。
        // 关键点：事件触发时调用的线程（通常仍在事务内）直接读取项目配置，可读到本次尚未提交的
        // 最新勾选/项目名称；若改为在异步线程里再查库，可能读到事务提交前的旧数据，甚至项目尚未入库，
        // 造成通知丢失或发送到过期渠道，因此这里先取快照、后异步投递。
        Project project;
        try {
            project = projectMapper.findById(projectId);
        } catch (Exception e) {
            logger.warn("项目通知快照读取失败: projectId={}, event={}", projectId, eventCode, e);
            return;
        }
        if (project == null) {
            return;
        }
        List<String> events = parseStringArray(project.getNotifyEvents());
        if (!events.contains(eventCode)) {
            return; // 项目未勾选该通知事项
        }
        List<Long> channelIds = parseLongArray(project.getNotifyChannels());
        if (channelIds.isEmpty()) {
            return; // 项目未绑定通知渠道
        }
        NotifyEventEnum event = NotifyEventEnum.fromCode(eventCode);
        String eventLabel = event != null ? event.getDescription() : eventCode;
        // 内容附加项目名称前缀，便于接收端识别来源
        String finalContent = "项目「" + project.getName() + "」" + (content != null ? content : "");
        String finalTitle = title != null ? title : eventLabel;
        List<Long> targetChannelIds = new ArrayList<>(channelIds);
        // 异步发送：通知失败不影响业务主流程，且使用已捕获的快照数据（避开通话线程事务未提交的读取差异）
        CompletableFuture.runAsync(() -> doSend(targetChannelIds, finalTitle, finalContent), notifyExecutor);
    }

    /** 执行项目通知：逐渠道发送（使用已捕获的快照数据，不再重新查库） */
    private void doSend(List<Long> channelIds, String finalTitle, String finalContent) {
        for (Long channelId : channelIds) {
            try {
                String err = sendByChannelId(channelId, finalTitle, finalContent);
                if (err != null) {
                    logger.warn("项目通知发送失败: channelId={}, 原因: {}", channelId, err);
                }
            } catch (Exception e) {
                logger.warn("项目通知发送异常: channelId={}", channelId, e);
            }
        }
    }

    /** 按渠道类型路由到对应发送器 */
    private String dispatch(NotifyChannel channel, String title, String content) {
        NotifySender sender = senderMap.get(channel.getType());
        if (sender == null) {
            return "不支持的通知方式: " + channel.getType();
        }
        try {
            return sender.send(channel, title, content);
        } catch (Exception e) {
            logger.warn("通知发送异常: channelId={}, type={}", channel.getId(), channel.getType(), e);
            return "发送异常: " + e.getMessage();
        }
    }

    /** 解析 JSON 数组字符串为 Long 列表，空/非法返回空列表 */
    private List<Long> parseLongArray(String json) {
        try {
            List<Long> list = JSON.parseArray(json, Long.class);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** 解析 JSON 数组字符串为 String 列表，空/非法返回空列表 */
    private List<String> parseStringArray(String json) {
        try {
            List<String> list = JSON.parseArray(json, String.class);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    @PreDestroy
    public void shutdown() {
        notifyExecutor.shutdownNow();
    }
}
