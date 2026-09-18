package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.mapper.ChatHistoryMapper;
import com.agent.hopaw.infra.model.dto.ChatHistoryVO;
import com.agent.hopaw.infra.model.entity.Agent;
import com.agent.hopaw.infra.model.entity.ChatHistory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChatHistoryService implements IChatHistoryService{

    private final ChatHistoryMapper chatHistoryMapper;
    private final IAgentService agentService;

    public ChatHistoryService(ChatHistoryMapper chatHistoryMapper, IAgentService agentService) {
        this.chatHistoryMapper = chatHistoryMapper;
        this.agentService = agentService;
    }

    @Override
    public List<ChatHistoryVO> findBySessionId(String sessionId, int limit) {
        return toVoList(chatHistoryMapper.findBySessionId(sessionId, limit));
    }

    @Override
    public List<ChatHistoryVO> findBySessionIdBefore(String sessionId, LocalDateTime beforeTime, Long beforeId, int limit) {
        return toVoList(chatHistoryMapper.findBySessionIdBefore(sessionId, beforeTime, beforeId, limit));
    }

    /** 实体转 VO，并填充所属 Agent 信息 */
    private List<ChatHistoryVO> toVoList(List<ChatHistory> list) {
        if(list == null || list.size() == 0){
            return new ArrayList<>(0);
        }
        List<Long> agentIds = list.stream().map(x -> x.getAgentId()).distinct().collect(Collectors.toList());
        List<Agent> agents = agentService.getAgentByIds(agentIds);
        List<ChatHistoryVO> result = new ArrayList<>();
        for (ChatHistory chatHistory : list) {
            //找到Agent 创建 VO
            Agent agent = agents.stream().filter(x -> x.getId().equals(chatHistory.getAgentId())).findFirst().orElse(null);
            ChatHistoryVO chatHistoryVO = new ChatHistoryVO();
            BeanUtils.copyProperties(chatHistory,chatHistoryVO);
            chatHistoryVO.setAgent(agent);
            result.add(chatHistoryVO);
        }
        return result;
    }

    @Override
    public int deleteBySessionId(String sessionId) {
        return chatHistoryMapper.deleteBySessionId(sessionId);
    }

    @Override
    public int countToolCallsBySessionId(String sessionId) {
        return chatHistoryMapper.countToolCallsBySessionId(sessionId);
    }
}
