package com.agent.hopaw.mapper;

import com.agent.hopaw.model.AiModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AiModelMapper {
    List<AiModel> findAll();

    List<AiModel> findByProviderId(@Param("providerId") Long providerId);

    AiModel findById(@Param("id") Long id);

    int insert(AiModel aiModel);

    int update(AiModel aiModel);

    int deleteById(@Param("id") Long id);
}
