package com.jd.genie.handler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.jd.genie.agent.enums.ResponseTypeEnum;
import com.jd.genie.model.multi.EventMessage;
import com.jd.genie.model.multi.EventResult;
import com.jd.genie.model.req.AgentRequest;
import com.jd.genie.model.response.AgentResponse;
import com.jd.genie.model.response.GptProcessResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.jd.genie.model.constant.Constants.RUNNING;
import static com.jd.genie.model.constant.Constants.SUCCESS;

@Slf4j
@Component
public class BaseAgentResponseHandler {
    protected GptProcessResult buildIncrResult(AgentRequest request, EventResult eventResult, AgentResponse agentResponse) {
        GptProcessResult streamResult = new GptProcessResult();
        streamResult.setResponseType(ResponseTypeEnum.text.name());
        streamResult.setStatus(agentResponse.getFinish() ? SUCCESS : RUNNING);
        streamResult.setFinished(agentResponse.getFinish());
        if ("result".equals(agentResponse.getMessageType())) {
            streamResult.setResponse(agentResponse.getResult());
            streamResult.setResponseAll(agentResponse.getResult());
        }
        streamResult.setReqId(request.getRequestId());

        String agentType = (Objects.nonNull(agentResponse.getResultMap())
                && agentResponse.getResultMap().containsKey("agentType"))
                ? String.valueOf(agentResponse.getResultMap().get("agentType")) : null;

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("agentType", agentType);
        resultMap.put("multiAgent", new HashMap<>());
        resultMap.put("eventData", new HashMap<>());

        // 增量数据
        EventMessage message = EventMessage.builder()
                .messageId(agentResponse.getMessageId())
                .build();
        boolean isFinal = Boolean.TRUE.equals(agentResponse.getIsFinal());
        boolean isFilterFinal = (Objects.nonNull(agentResponse.getResultMap())
                && agentResponse.getMessageType().equals("deep_search")
                && agentResponse.getResultMap().containsKey("messageType")
                && agentResponse.getResultMap().get("messageType").equals("extend"));

        switch (agentResponse.getMessageType()) {
            case "plan_thought":
                message.setMessageType(agentResponse.getMessageType());
                message.setMessageOrder(eventResult.getAndIncrOrder(agentResponse.getMessageType()));
                message.setResultMap(JSON.parseObject(JSONObject.toJSONString(agentResponse)));
                if (isFinal && !eventResult.getResultMap().containsKey("plan_thought")) {
                    eventResult.getResultMap().put("plan_thought", agentResponse.getPlanThought());
                }
                break;
            case "plan":
                if (eventResult.isInitPlan()) {
                    // plan 生成
                    message.setMessageType(agentResponse.getMessageType());
                    message.setMessageOrder(1);
                    message.setResultMap(agentResponse.getPlan());
                    if (isFinal) {
                        eventResult.getResultMap().put("plan", agentResponse.getPlan());
                    }
                } else {
                    // plan 更新，需要关联 task
                    message.setTaskId(eventResult.getTaskId());
                    message.setTaskOrder(eventResult.getTaskOrder().getAndIncrement());
                    message.setMessageType("task");
                    message.setMessageOrder(1);
                    message.setResultMap(JSON.parseObject(JSONObject.toJSONString(agentResponse)));
                    if (isFinal) {
                        eventResult.setResultMapSubTask(message.getResultMap());
                    }
                }
                break;
            case "task":
                message.setTaskId(eventResult.renewTaskId());
                message.setTaskOrder(eventResult.getTaskOrder().getAndIncrement());
                message.setMessageType(agentResponse.getMessageType());
                message.setMessageOrder(1);
                message.setResultMap(JSON.parseObject(JSONObject.toJSONString(agentResponse)));
                if (isFinal) {
                    List<Object> task = new ArrayList<>();
                    task.add(message.getResultMap());
                    eventResult.setResultMapTask(task);
                }
                break;
            default:
                message.setTaskId(eventResult.getTaskId());
                message.setTaskOrder(eventResult.getTaskOrder().getAndIncrement());
                message.setMessageType("task");
                message.setMessageOrder(1);
                if (eventResult.getStreamTaskMessageType().contains(agentResponse.getMessageType())) {
                    String orderKey = eventResult.getTaskId() + ":" + agentResponse.getMessageType();
                    message.setMessageOrder(eventResult.getAndIncrOrder(orderKey));
                }
                message.setResultMap(JSON.parseObject(JSONObject.toJSONString(agentResponse)));
                if (isFinal && !isFilterFinal) {
                    eventResult.setResultMapSubTask(message.getResultMap());
                }
                break;
        }

        // 增量缓存
        resultMap.put("eventData", JSONObject.parseObject(JSON.toJSONString(message)));
        streamResult.setResultMap(resultMap);
        return streamResult;
    }
}
