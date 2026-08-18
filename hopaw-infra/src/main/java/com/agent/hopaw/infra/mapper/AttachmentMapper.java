package com.agent.hopaw.infra.mapper;

import com.agent.hopaw.infra.model.entity.Attachment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AttachmentMapper {

    int insert(Attachment attachment);

    int update(Attachment attachment);

    int deleteById(@Param("id") Long id);

    Attachment findById(@Param("id") Long id);

    List<Attachment> findByUserIdWithFilters(@Param("userId") String userId,
                                             @Param("keyword") String keyword,
                                             @Param("source") String source,
                                             @Param("tag") String tag,
                                             @Param("fileType") String fileType,
                                             @Param("offset") int offset,
                                             @Param("size") int size);

    int countByUserIdWithFilters(@Param("userId") String userId,
                                 @Param("keyword") String keyword,
                                 @Param("source") String source,
                                 @Param("tag") String tag,
                                 @Param("fileType") String fileType);

    int countByStoragePath(@Param("storagePath") String storagePath);
}
