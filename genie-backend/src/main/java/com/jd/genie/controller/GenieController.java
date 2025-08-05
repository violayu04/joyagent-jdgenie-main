package com.jd.genie.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.agent.printer.Printer;
import com.jd.genie.agent.printer.ChatSessionSSEPrinter;
import com.jd.genie.agent.tool.ToolCollection;
import com.jd.genie.agent.tool.common.CodeInterpreterTool;
import com.jd.genie.agent.tool.common.DeepSearchTool;
import com.jd.genie.agent.tool.common.FileTool;
import com.jd.genie.agent.tool.common.ReportTool;
import com.jd.genie.agent.tool.mcp.McpTool;
import com.jd.genie.agent.util.DateUtil;
import com.jd.genie.agent.util.ThreadUtil;
import com.jd.genie.config.GenieConfig;
import com.jd.genie.model.req.AgentRequest;
import com.jd.genie.model.req.GptQueryReq;
import com.jd.genie.entity.ChatSession;
import com.jd.genie.entity.User;
import com.jd.genie.service.AgentHandlerService;
import com.jd.genie.service.ChatSessionService;
import com.jd.genie.service.IGptProcessService;
import com.jd.genie.service.UserService;
import com.jd.genie.service.impl.AgentHandlerFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/")
public class GenieController {
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(5);
    private static final long HEARTBEAT_INTERVAL = 10_000L; // 10秒心跳间隔
    @Autowired
    protected GenieConfig genieConfig;
    @Autowired
    private AgentHandlerFactory agentHandlerFactory;
    @Autowired
    private IGptProcessService gptProcessService;
    @Autowired
    private UserService userService;
    @Autowired
    private ChatSessionService chatSessionService;

    /**
     * 开启SSE心跳
     * @param emitter
     * @param requestId
     * @return
     */
    private ScheduledFuture<?> startHeartbeat(SseEmitter emitter, String requestId) {
        return executor.scheduleAtFixedRate(() -> {
            try {
                // 发送心跳消息
                log.info("{} send heartbeat", requestId);
                emitter.send("heartbeat");
            } catch (Exception e) {
                // 发送心跳失败，关闭连接
                log.error("{} heartbeat failed, closing connection", requestId, e);
                emitter.completeWithError(e);
            }
        }, HEARTBEAT_INTERVAL, HEARTBEAT_INTERVAL, TimeUnit.MILLISECONDS);
    }

    /**
     * 注册SSE事件
     * @param emitter
     * @param requestId
     * @param heartbeatFuture
     */
    private void registerSSEMonitor(SseEmitter emitter, String requestId, ScheduledFuture<?> heartbeatFuture) {
        // 监听SSE异常事件
        emitter.onCompletion(() -> {
            log.info("{} SSE connection completed normally", requestId);
            heartbeatFuture.cancel(true);
        });

        // 监听连接超时事件
        emitter.onTimeout(() -> {
            log.info("{} SSE connection timed out", requestId);
            heartbeatFuture.cancel(true);
            emitter.complete();
        });

        // 监听连接错误事件
        emitter.onError((ex) -> {
            log.info("{} SSE connection error: ", requestId, ex);
            heartbeatFuture.cancel(true);
            emitter.completeWithError(ex);
        });
    }

    /**
     * 执行智能体调度
     * @param request
     * @param authentication
     * @return
     * @throws UnsupportedEncodingException
     */
    @PostMapping("/AutoAgent")
    public SseEmitter AutoAgent(@RequestBody AgentRequest request, Authentication authentication) throws UnsupportedEncodingException {

        log.info("{} auto agent request: {}", request.getRequestId(), JSON.toJSONString(request));

        // 获取当前用户
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        User currentUser = userService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        // 验证会话存在性（用户消息应已通过单独API保存）
        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new RuntimeException("会话ID不能为空，请先保存用户消息");
        }
        
        ChatSession chatSession = chatSessionService.getSessionByIdAndUser(sessionId, currentUser).orElse(null);
        if (chatSession == null) {
            throw new RuntimeException("会话不存在或无权限访问");
        }

        Long AUTO_AGENT_SSE_TIMEOUT = 60 * 60 * 1000L;

        SseEmitter emitter = new SseEmitter(AUTO_AGENT_SSE_TIMEOUT);
        // SSE心跳
        ScheduledFuture<?> heartbeatFuture = startHeartbeat(emitter, request.getRequestId());
        // 监听SSE事件
        registerSSEMonitor(emitter, request.getRequestId(), heartbeatFuture);
        // 拼接输出类型
        request.setQuery(handleOutputStyle(request));
        // 执行调度引擎
        ThreadUtil.execute(() -> {
            try {
                Printer printer = new ChatSessionSSEPrinter(emitter, request, request.getAgentType(), chatSessionService);
                AgentContext agentContext = AgentContext.builder()
                        .requestId(request.getRequestId())
                        .sessionId(request.getSessionId())  // 使用实际的会话ID
                        .printer(printer)
                        .query(request.getQuery())
                        .task("")
                        .dateInfo(DateUtil.CurrentDateInfo())
                        .productFiles(new ArrayList<>())
                        .taskProductFiles(new ArrayList<>())
                        .sopPrompt(request.getSopPrompt())
                        .basePrompt(request.getBasePrompt())
                        .agentType(request.getAgentType())
                        .isStream(Objects.nonNull(request.getIsStream()) ? request.getIsStream() : false)
                        .build();

                // 构建工具列表
                agentContext.setToolCollection(buildToolCollection(agentContext, request));
                // 根据数据类型获取对应的处理器
                AgentHandlerService handler = agentHandlerFactory.getHandler(agentContext, request);
                // 执行处理逻辑
                handler.handle(agentContext, request);
                // 关闭连接
                emitter.complete();

            } catch (Exception e) {
                log.error("{} auto agent error", request.getRequestId(), e);
            }
        });

        return emitter;
    }


    /**
     * html模式： query+以 html展示
     * docs模式：query+以 markdown展示
     * table 模式: query+以 excel 展示
     */
    private String handleOutputStyle(AgentRequest request) {
        String query = request.getQuery();
        Map<String, String> outputStyleMap = genieConfig.getOutputStylePrompts();
        if (!StringUtils.isEmpty(request.getOutputStyle())) {
            query += outputStyleMap.computeIfAbsent(request.getOutputStyle(), k -> "");
        }
        return query;
    }


    /**
     * 构建工具列表
     *
     * @param agentContext
     * @param request
     * @return
     */
    private ToolCollection buildToolCollection(AgentContext agentContext, AgentRequest request) {

        ToolCollection toolCollection = new ToolCollection();
        toolCollection.setAgentContext(agentContext);
        // file
        FileTool fileTool = new FileTool();
        fileTool.setAgentContext(agentContext);
        toolCollection.addTool(fileTool);

        // default tool
        List<String> agentToolList = Arrays.asList(genieConfig.getMultiAgentToolListMap()
                .getOrDefault("default", "search,code,report").split(","));
        if (!agentToolList.isEmpty()) {
            if (agentToolList.contains("code")) {
                CodeInterpreterTool codeTool = new CodeInterpreterTool();
                codeTool.setAgentContext(agentContext);
                toolCollection.addTool(codeTool);
            }
            if (agentToolList.contains("report")) {
                ReportTool htmlTool = new ReportTool();
                htmlTool.setAgentContext(agentContext);
                toolCollection.addTool(htmlTool);
            }
            if (agentToolList.contains("search")) {
                DeepSearchTool deepSearchTool = new DeepSearchTool();
                deepSearchTool.setAgentContext(agentContext);
                toolCollection.addTool(deepSearchTool);
            }
        }

        // mcp tool
        try {
            McpTool mcpTool = new McpTool();
            mcpTool.setAgentContext(agentContext);
            for (String mcpServer : genieConfig.getMcpServerUrlArr()) {
                String listToolResult = mcpTool.listTool(mcpServer);
                if (listToolResult.isEmpty()) {
                    log.error("{} mcp server {} invalid", agentContext.getRequestId(), mcpServer);
                    continue;
                }

                JSONObject resp = JSON.parseObject(listToolResult);
                if (resp.getIntValue("code") != 200) {
                    log.error("{} mcp serve {} code: {}, message: {}", agentContext.getRequestId(), mcpServer,
                            resp.getIntValue("code"), resp.getString("message"));
                    continue;
                }
                JSONArray data = resp.getJSONArray("data");
                if (data.isEmpty()) {
                    log.error("{} mcp serve {} code: {}, message: {}", agentContext.getRequestId(), mcpServer,
                            resp.getIntValue("code"), resp.getString("message"));
                    continue;
                }
                for (int i = 0; i < data.size(); i++) {
                    JSONObject tool = data.getJSONObject(i);
                    String method = tool.getString("name");
                    String description = tool.getString("description");
                    String inputSchema = tool.getString("inputSchema");
                    toolCollection.addMcpTool(method, description, inputSchema, mcpServer);
                }
            }
        } catch (Exception e) {
            log.error("{} add mcp tool failed", agentContext.getRequestId(), e);
        }

        return toolCollection;
    }

    /**
     * 从查询文本中提取标题
     * @param query
     * @return
     */
    private String extractTitleFromQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return "新对话";
        }
        
        // 取前30个字符作为标题，如果超过30个字符则截断并加省略号
        String title = query.trim();
        if (title.length() > 30) {
            title = title.substring(0, 30) + "...";
        }
        
        return title;
    }

    /**
     * 立即保存用户消息到会话历史
     * @param request
     * @param authentication
     * @return
     */
    @PostMapping("/web/api/v1/chat/saveUserMessage")
    public ResponseEntity<Map<String, Object>> saveUserMessage(@RequestBody AgentRequest request, Authentication authentication) {
        log.info("{} save user message request: sessionId={}, query={}", request.getRequestId(), request.getSessionId(), request.getQuery());

        try {
            // 获取当前用户
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User currentUser = userService.findByUsername(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("用户不存在"));

            // 处理会话
            ChatSession chatSession = null;
            String sessionId = request.getSessionId();
            
            if (sessionId != null && !sessionId.trim().isEmpty()) {
                // 验证会话是否属于当前用户
                chatSession = chatSessionService.getSessionByIdAndUser(sessionId, currentUser).orElse(null);
                if (chatSession == null) {
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("success", false);
                    errorResponse.put("error", "会话不存在或无权限访问");
                    return ResponseEntity.badRequest().body(errorResponse);
                }
            } else {
                // 创建新会话
                String title = extractTitleFromQuery(request.getQuery());
                chatSession = chatSessionService.createSession(currentUser, title);
                sessionId = chatSession.getSessionId();
            }

            // 保存用户消息
            int messageOrder = chatSessionService.getSessionMessages(chatSession).size();
            chatSessionService.saveMessage(chatSession, "user", request.getQuery(), messageOrder);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("sessionId", sessionId);
            response.put("message", "用户消息已保存");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("{} save user message error", request.getRequestId(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * 探活接口
     *
     * @return
     */
    @RequestMapping(value = "/web/health", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ok");
    }


    /**
     * 处理Agent流式增量查询请求，返回SSE事件流
     * @param params 查询请求参数对象，包含GPT查询所需信息
     * @return 返回SSE事件发射器，用于流式传输增量响应结果
     */
    @RequestMapping(value = "/web/api/v1/gpt/queryAgentStreamIncr", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter queryAgentStreamIncr(@RequestBody GptQueryReq params) {
        return gptProcessService.queryMultiAgentIncrStream(params);
    }

}
    