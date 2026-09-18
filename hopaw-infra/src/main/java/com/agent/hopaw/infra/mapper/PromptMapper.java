package com.agent.hopaw.infra.mapper;

import com.agent.hopaw.infra.model.entity.Prompt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PromptMapper {

    int insert(Prompt prompt);

    int update(Prompt prompt);

    int deleteById(@Param("id") Long id);

    Prompt findById(@Param("id") Long id);

    List<Prompt> findByUserId(@Param("userId") String userId,
                              @Param("keyword") String keyword,
                              @Param("tag") String tag,
                              @Param("sortBy") String sortBy,
                              @Param("offset") int offset,
                              @Param("size") int size);

    int countByUserId(@Param("userId") String userId,
                      @Param("keyword") String keyword,
                      @Param("tag") String tag);

    int incrementHeat(@Param("id") Long id);

    List<String> findAllTags(@Param("userId") String userId);
}
