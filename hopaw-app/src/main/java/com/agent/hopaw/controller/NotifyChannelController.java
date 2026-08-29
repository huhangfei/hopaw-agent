package com.agent.hopaw.controller;

import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.infra.model.entity.NotifyChannel;
import com.agent.hopaw.infra.service.INotificationService;
import com.agent.hopaw.infra.service.INotifyChannelService;
import com.agent.hopaw.util.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

/**
 * 通知渠道管理接口：渠道的增删改查与测试发送。
 */
@Controller
public class NotifyChannelController {
    private static final Logger logger = LoggerFactory.getLogger(NotifyChannelController.class);

    private final INotifyChannelService notifyChannelService;
    private final INotificationService notificationService;

    public NotifyChannelController(INotifyChannelService notifyChannelService,
                                   INotificationService notificationService) {
        this.notifyChannelService = notifyChannelService;
        this.notificationService = notificationService;
    }

    // 查询当前用户全部通知渠道
    @GetMapping("/api/notify/channels")
    @ResponseBody
    public ResponseBean listChannels(HttpServletRequest request) {
        String userId = CurrentUser.require(request);
        return ResponseBean.success(notifyChannelService.listByUser(userId));
    }

    // 创建通知渠道
    @PostMapping("/api/notify/channels")
    @ResponseBody
    public ResponseBean createChannel(HttpServletRequest request, @RequestBody NotifyChannel channel) {
        String userId = CurrentUser.require(request);
        try {
            channel.setUserId(userId);
            NotifyChannel created = notifyChannelService.createChannel(channel);
            return ResponseBean.success(created);
        } catch (Exception e) {
            logger.warn("创建通知渠道失败: {}", e.getMessage());
            return ResponseBean.fail(e.getMessage());
        }
    }

    // 更新通知渠道
    @PutMapping("/api/notify/channels/{id}")
    @ResponseBody
    public ResponseBean updateChannel(HttpServletRequest request, @PathVariable Long id, @RequestBody NotifyChannel channel) {
        String userId = CurrentUser.require(request);
        channel.setId(id);
        try {
            NotifyChannel updated = notifyChannelService.updateChannel(channel, userId);
            return ResponseBean.success(updated);
        } catch (Exception e) {
            logger.warn("更新通知渠道失败: id={}, {}", id, e.getMessage());
            return ResponseBean.fail(e.getMessage());
        }
    }

    // 删除通知渠道
    @DeleteMapping("/api/notify/channels/{id}")
    @ResponseBody
    public ResponseBean deleteChannel(HttpServletRequest request, @PathVariable Long id) {
        String userId = CurrentUser.require(request);
        try {
            boolean ok = notifyChannelService.deleteChannel(id, userId);
            return ok ? ResponseBean.success("删除成功") : ResponseBean.fail("通知渠道不存在或已删除");
        } catch (Exception e) {
            logger.warn("删除通知渠道失败: id={}", id, e);
            return ResponseBean.fail(e.getMessage());
        }
    }

    // 测试发送：向指定渠道发送一条测试通知，返回发送结果（成功/失败原因）
    @PostMapping("/api/notify/channels/{id}/test")
    @ResponseBody
    public ResponseBean testChannel(HttpServletRequest request, @PathVariable Long id) {
        String userId = CurrentUser.require(request);
        try {
            NotifyChannel channel = notifyChannelService.getChannel(id, userId);
            if (channel == null) {
                return ResponseBean.fail("通知渠道不存在或无权访问");
            }
            String err = notificationService.sendByChannelId(id, "测试通知", "这是一条来自通知渠道测试的消息，收到即表示渠道配置正常。");
            return err == null ? ResponseBean.success("发送成功") : ResponseBean.fail(err);
        } catch (Exception e) {
            logger.warn("测试通知渠道失败: id={}", id, e);
            return ResponseBean.fail(e.getMessage() != null ? e.getMessage() : "测试发送失败（未知异常）");
        }
    }
}
