package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.model.entity.Attachment;

import java.nio.file.Path;
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
     * 上传并保存附件：将文件内容写入附件存储目录并创建附件记录（文件类型按扩展名识别）。
     * 契约层不依赖 Spring Web，因此以字节内容而非 MultipartFile 传递。
     *
     * @param userId        上传人
     * @param originalName  原始文件名
     * @param contentType   MIME 类型（可为 null）
     * @param content       文件内容字节
     * @param source        来源标识
     * @param bizId         业务关联ID（可为 null）
     * @return 附件记录（含主键）
     */
    Attachment uploadAttachment(String userId, String originalName, String contentType, byte[] content, String source, Long bizId);

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
     * 根据存储路径统计引用数
     */
    int countByStoragePath(String storagePath);

    /**
     * 获取决定路径
     * @param id
     * @return
     */
    Path getAbsolutePath(Long id);
}
