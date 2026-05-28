package com.agent.hopaw.infra.model.dto;

import com.agent.hopaw.infra.model.entity.Agent;
import com.agent.hopaw.infra.model.entity.ChatHistory;

/**
 * @author hhf
 */
public class ChatHistoryVO extends ChatHistory {
    private Agent agent;
    public Agent getAgent() {
        return agent;
    }
    public void setAgent(Agent agent) {
        this.agent = agent;
    }
}
