package com.jd.genie.handler;

import com.jd.genie.agent.enums.AgentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Configuration
public class AgentHandlerConfig {

    @Autowired
    private List<AgentResponseHandler> handlerList;

    @Bean
    public Map<AgentType, AgentResponseHandler> handlerMap() {
        Map<AgentType, AgentResponseHandler> map = new EnumMap<>(AgentType.class);
        for (AgentResponseHandler handler : handlerList) {
            if (handler instanceof PlanSolveAgentResponseHandler) {
                map.put(AgentType.PLAN_SOLVE, handler);
            } else if (handler instanceof ReactAgentResponseHandler) {
                map.put(AgentType.REACT, handler);
            }
            // 可扩展更多 handler
        }
        return map;
    }
}