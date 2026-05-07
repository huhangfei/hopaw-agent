package com.agent.hopaw.mapper;

import com.agent.hopaw.model.Agent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AgentMapper {
    List<Agent> findAll();
    
    Agent findById(@Param("id") Long id);
    
    int insert(Agent agent);
    
    int update(Agent agent);
    
    int deleteById(@Param("id") Long id);

    List<Agent> findByUserId(@Param("userId") String userId);
}
