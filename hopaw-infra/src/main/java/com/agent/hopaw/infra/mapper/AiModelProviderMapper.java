package com.agent.hopaw.infra.mapper;

import com.agent.hopaw.infra.model.entity.AiModelProvider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AiModelProviderMapper {
    List<AiModelProvider> findAll();

    AiModelProvider findById(@Param("id") Long id);

    int insert(AiModelProvider aiModelProvider);

    int update(AiModelProvider aiModelProvider);

    int deleteById(@Param("id") Long id);
}