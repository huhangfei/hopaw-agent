package com.agent.hopaw.mapper;

import com.agent.hopaw.model.ScheduledTask;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ScheduledTaskMapper {
    List<ScheduledTask> findAll();
    ScheduledTask findById(Long id);
    void insert(ScheduledTask task);
    void update(ScheduledTask task);
    void deleteById(Long id);
}
