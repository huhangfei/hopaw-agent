package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.mapper.ChatHistoryMapper;
import com.agent.hopaw.infra.model.dto.ChatHistoryVO;
import com.agent.hopaw.infra.model.entity.Agent;
import com.agent.hopaw.infra.model.entity.ChatHistory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

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
        List<ChatHistory> list = chatHistoryMapper.findBySessionId(sessionId, limit);
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
}
