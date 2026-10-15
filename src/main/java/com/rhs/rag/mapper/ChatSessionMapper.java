package com.rhs.rag.mapper;


import com.jichi.ragkb.entity.ChatSession;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.List;

public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    List<ChatSession> findByUserIdAndIsDeletedFalseOrderByLastActiveAtDesc(Long userId);
}