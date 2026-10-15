package com.rhs.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jichi.ragkb.entity.ChatMessage;


import java.util.List;

public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(String sessionId);
}