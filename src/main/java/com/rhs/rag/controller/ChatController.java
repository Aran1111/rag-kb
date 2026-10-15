package com.rhs.rag.controller;


import com.jichi.ragkb.entity.ChatMessage;
import com.jichi.ragkb.entity.ChatSession;

import com.rhs.rag.dto.ApiResponse;
import com.rhs.rag.dto.ChatRequest;
import com.rhs.rag.dto.RagResponse;
import com.rhs.rag.mapper.ChatMessageMapper;
import com.rhs.rag.mapper.ChatSessionMapper;
import com.rhs.rag.security.UserContext;
import com.rhs.rag.service.ChatSessionService;
import com.rhs.rag.service.PermissionService;
import com.rhs.rag.service.StreamingRagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 智能会话接口
 */
@Tag(name = "智能会话", description = "基于知识库的流式对话接口，支持会话管理和消息历史")
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final StreamingRagService streamingRagService;
    private final ChatSessionService sessionService;
    private final PermissionService permissionService;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;

    // 专用线程池处理 SSE 推送（避免占用 Tomcat 线程池）
    private final ExecutorService sseExecutor = Executors.newCachedThreadPool(
            r -> {
                Thread t = new Thread(r);
                t.setName("sse-rag-" + t.getId());
                t.setDaemon(true);
                return t;
            }
    );

    @Operation(
        summary = "流式问答（SSE）", 
        description = "基于Server-Sent Events的流式问答接口，支持实时字符流输出"
    )
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(
            @Parameter(description = "用户问题", example = "公司的年假政策是什么？", required = true)
            @RequestParam String question,
            
            @Parameter(description = "知识库ID列表", example = "[1, 2]", required = true)
            @RequestParam List<Long> kbIds,
            
            @Parameter(description = "会话ID，为空时创建新会话", example = "session-abc123")
            @RequestParam(required = false) String sessionId) {

        // 校验权限
        kbIds.forEach(permissionService::requireRead);

        // 创建或获取会话
        String sid = sessionService.getOrCreateSession(sessionId, kbIds);

        SseEmitter emitter = new SseEmitter(60_000L);  // 60秒超时

        // 捕获当前 HTTP 线程的用户上下文，传递给 SSE 线程
        Long currentUserId = UserContext.getUserId();
        String currentDeptId = UserContext.getDepartmentId();
        String currentRole = UserContext.getRole();

        sseExecutor.submit(() -> {
            UserContext.set(currentUserId, currentDeptId, currentRole);
            try {
                streamingRagService.streamQuery(question, kbIds, sid, emitter);
            } catch (Exception e) {
                log.error("[Chat] SSE 执行异常：{}", e.getMessage(), e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("{\"message\":\"系统内部错误，请稍后重试\"}"));
                    emitter.complete();
                } catch (IOException ignored) {}
            } finally {
                UserContext.clear();
            }
        });

        return emitter;
    }

    @Operation(
        summary = "同步问答", 
        description = "同步问答接口，测试用，返回完整响应"
    )
    @PostMapping
    public RagResponse syncChat(
            @Parameter(description = "聊天请求参数", required = true)
            @RequestBody ChatRequest request) {
        request.getKbIds().forEach(permissionService::requireRead);
        String sid = sessionService.getOrCreateSession(request.getSessionId(), request.getKbIds());
        return streamingRagService.syncQuery(request.getQuestion(), request.getKbIds(), sid);
    }
}