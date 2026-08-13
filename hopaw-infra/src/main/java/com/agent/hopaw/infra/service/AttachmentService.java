package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.mapper.AttachmentMapper;
import com.agent.hopaw.infra.model.entity.Attachment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AttachmentService implements IAttachmentService {

    private static final Logger logger = LoggerFactory.getLogger(AttachmentService.class);

    private final AttachmentMapper attachmentMapper;

    public AttachmentService(AttachmentMapper attachmentMapper) {
        this.attachmentMapper = attachmentMapper;
    }

    @Override
    public Attachment createAttachment(Attachment attachment) {
        attachmentMapper.insert(attachment);
        logger.info("附件记录创建成功: {} -> {}", attachment.getOriginalName(), attachment.getUrl());
        return attachment;
    }

    @Override
    public void deleteAttachment(Long id, String userId) {
        Attachment attachment = attachmentMapper.findById(id);
        if (attachment == null) {
            throw new RuntimeException("附件不存在");
        }
        if (!userId.equals(attachment.getUserId())) {
            throw new RuntimeException("无权删除该附件");
        }
        attachmentMapper.deleteById(id);
    }

    @Override
    public Attachment updateAttachment(Attachment attachment, String userId) {
        Attachment existing = attachmentMapper.findById(attachment.getId());
        if (existing == null) {
            throw new RuntimeException("附件不存在");
        }
        if (!userId.equals(existing.getUserId())) {
            throw new RuntimeException("无权修改该附件");
        }
        existing.setTags(attachment.getTags());
        existing.setRemark(attachment.getRemark());
        if (attachment.getSource() != null) {
            existing.setSource(attachment.getSource());
        }
        if (attachment.getBizId() != null) {
            existing.setBizId(attachment.getBizId());
        }
        attachmentMapper.update(existing);
        return existing;
    }

    @Override
    public Attachment getAttachment(Long id, String userId) {
        Attachment attachment = attachmentMapper.findById(id);
        if (attachment == null) {
            return null;
        }
        if (!userId.equals(attachment.getUserId())) {
            return null;
        }
        return attachment;
    }

    @Override
    public List<Attachment> getAttachmentsPage(String userId, String keyword, String source, String tag, String fileType, int page, int size) {
        int offset = (page - 1) * size;
        return attachmentMapper.findByUserIdWithFilters(userId, keyword, source, tag, fileType, offset, size);
    }

    @Override
    public int countAttachments(String userId, String keyword, String source, String tag, String fileType) {
        return attachmentMapper.countByUserIdWithFilters(userId, keyword, source, tag, fileType);
    }

    @Override
    public List<Attachment> getAttachmentsByBiz(String source, Long bizId) {
        return attachmentMapper.findByBizTypeAndBizId(source, bizId);
    }

    @Override
    public int countByStoragePath(String storagePath) {
        return attachmentMapper.countByStoragePath(storagePath);
    }
}
