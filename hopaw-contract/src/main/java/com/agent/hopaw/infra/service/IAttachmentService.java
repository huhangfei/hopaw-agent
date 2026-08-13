package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.model.entity.Attachment;

import java.util.List;

/**
 * 附件管理服务接口
 */
public interface IAttachmentService {

    /**
     * 创建附件记录
     */
    Attachment createAttachment(Attachment attachment);

    /**
     * 删除附件
     */
    void deleteAttachment(Long id, String userId);

    /**
     * 更新附件（标签、备注）
     */
    Attachment updateAttachment(Attachment attachment, String userId);

    /**
     * 获取附件详情
     */
    Attachment getAttachment(Long id, String userId);

    /**
     * 分页查询附件
     */
    List<Attachment> getAttachmentsPage(String userId, String keyword, String source, String tag, String fileType, int page, int size);

    /**
     * 统计附件数量
     */
    int countAttachments(String userId, String keyword, String source, String tag, String fileType);

    /**
     * 根据来源和业务ID查询附件
     */
    List<Attachment> getAttachmentsByBiz(String source, Long bizId);

    /**
     * 根据存储路径统计引用数
     */
    int countByStoragePath(String storagePath);
}
