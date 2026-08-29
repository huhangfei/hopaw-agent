package com.agent.hopaw.infra.mapper;

import com.agent.hopaw.infra.model.entity.NotifyChannel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NotifyChannelMapper {

    int insert(NotifyChannel channel);

    int update(NotifyChannel channel);

    int deleteById(@Param("id") Long id);

    NotifyChannel findById(@Param("id") Long id);

    List<NotifyChannel> findByUserId(@Param("userId") String userId);
}
