package com.rhs.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jichi.ragkb.entity.ChatMessage;
import com.jichi.ragkb.entity.ChatSession;

import com.rhs.rag.mapper.ChatMessageMapper;
import com.rhs.rag.mapper.ChatSessionMapper;
import com.rhs.rag.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.validation.constraints.Min;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatSessionService {

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ObjectMapper objectMapper;

    /** 最多保留的历史轮数（超出后截断旧消息） */
    private static final int MAX_HISTORY_ROUNDS = 5;

    /**
     * 获取已有会话，或创建新会话。
     */
    @Transactional
    public String getOrCreateSession(String sessionId, List<Long> kbIds) {
        if (sessionId != null && !sessionId.isBlank()) {
            ChatSession chatSession = chatSessionMapper.selectById(sessionId);
            if (chatSession != null){
                chatSession.setLastActiveAt(LocalDateTime.now());
                chatSessionMapper.updateById(chatSession);
                return sessionId;
            }
        }

        // 创建新会话
        ChatSession session = new ChatSession();
        session.setId(UUID.randomUUID().toString());
        session.setUserId(UserContext.getUserId());
        session.setKbIds(kbIds.toString());
        session.setMessageCount(0);
        session.setLastActiveAt(LocalDateTime.now());
        session.setCreatedAt(LocalDateTime.now());
        session.setIsDeleted(false);
        chatSessionMapper.insert(session);

        log.info("[ChatSession] 新建会话：sessionId={}，userId={}",
                session.getId(), UserContext.getUserId());
        return session.getId();
    }

    /**
     * 保存一轮对话（用户问题 + 助手回答）。
     */
    @Transactional
    public void saveMessage(String sessionId, String question, String answer,
                             String sourcesJson, int latencyMs) {
        // 保存用户消息
        ChatMessage userMsg = new ChatMessage();
        userMsg.setSessionId(sessionId);
        userMsg.setRole("USER");
        userMsg.setContent(question);
        chatMessageMapper.insert(userMsg);

        // 保存助手回答
        ChatMessage assistantMsg = new ChatMessage();
        assistantMsg.setSessionId(sessionId);
        assistantMsg.setRole("ASSISTANT");
        assistantMsg.setContent(answer);
        assistantMsg.setSources(sourcesJson);
        assistantMsg.setLatencyMs(latencyMs);
        chatMessageMapper.insert(assistantMsg);

        // 更新会话消息数和活跃时间
        ChatSession chatSession = chatSessionMapper.selectById(sessionId);
        if (chatSession != null){
            chatSession.setLastActiveAt(LocalDateTime.now());
            chatSession.setMessageCount(chatSession.getMessageCount() + 2);
            if (chatSession.getTitle() == null && !question.isEmpty()){
                chatSession.setTitle(question.substring(0, Math.min(50,question.length())));
            }
            chatSessionMapper.updateById(chatSession);
        }
    }

    /**
     * 获取会话历史（用于多轮对话上下文）。
     * 最近 MAX_HISTORY_ROUNDS 轮，不含当前问题。
     */
    public List<ChatMessage> getHistory(String sessionId) {
        List<ChatMessage> all = chatMessageMapper
                .selectList(new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId,sessionId)
                        .orderByAsc(ChatMessage::getCreatedAt));

        // 取最近的 N 轮（N * 2 条消息：用户 + 助手）
        int maxMessages = MAX_HISTORY_ROUNDS * 2;
        if (all.size() > maxMessages) {
            all = all.subList(all.size() - maxMessages, all.size());
        }
        return all;
    }
}