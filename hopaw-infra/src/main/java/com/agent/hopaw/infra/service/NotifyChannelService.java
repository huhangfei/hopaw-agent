package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.constant.NotifyChannelTypeEnum;
import com.agent.hopaw.infra.mapper.NotifyChannelMapper;
import com.agent.hopaw.infra.model.entity.NotifyChannel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 通知渠道管理服务实现。
 */
@Service
public class NotifyChannelService implements INotifyChannelService {
    private final NotifyChannelMapper notifyChannelMapper;

    public NotifyChannelService(NotifyChannelMapper notifyChannelMapper) {
        this.notifyChannelMapper = notifyChannelMapper;
    }

    @Override
    public NotifyChannel createChannel(NotifyChannel channel) {
        validate(channel);
        if (channel.getEnabled() == null) {
            channel.setEnabled(true);
        }
        notifyChannelMapper.insert(channel);
        return channel;
    }

    @Override
    public NotifyChannel updateChannel(NotifyChannel channel, String userId) {
        NotifyChannel existing = getChannel(channel.getId(), userId);
        if (existing == null) {
            throw new RuntimeException("通知渠道不存在或无权访问");
        }
        validate(channel);
        existing.setName(channel.getName());
        existing.setType(channel.getType());
        existing.setConfig(channel.getConfig());
        existing.setEnabled(channel.getEnabled() != null ? channel.getEnabled() : true);
        notifyChannelMapper.update(existing);
        return existing;
    }

    @Override
    public boolean deleteChannel(Long id, String userId) {
        NotifyChannel existing = getChannel(id, userId);
        if (existing == null) {
            return false;
        }
        return notifyChannelMapper.deleteById(id) > 0;
    }

    @Override
    public NotifyChannel getChannel(Long id, String userId) {
        NotifyChannel channel = notifyChannelMapper.findById(id);
        if (channel == null || !channel.getUserId().equals(userId)) {
            return null;
        }
        return channel;
    }

    @Override
    public List<NotifyChannel> listByUser(String userId) {
        List<NotifyChannel> list = notifyChannelMapper.findByUserId(userId);
        return list != null ? list : new ArrayList<>();
    }

    /** 基础校验：名称、类型、配置非空且类型合法 */
    private void validate(NotifyChannel channel) {
        if (channel.getName() == null || channel.getName().trim().isEmpty()) {
            throw new RuntimeException("渠道名称不能为空");
        }
        if (NotifyChannelTypeEnum.fromCode(channel.getType()) == null) {
            throw new RuntimeException("不支持的通知方式: " + channel.getType());
        }
        if (channel.getConfig() == null || channel.getConfig().trim().isEmpty()) {
            throw new RuntimeException("渠道配置不能为空");
        }
    }
}
