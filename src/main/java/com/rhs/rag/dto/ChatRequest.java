package com.rhs.rag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.List;

@Data
@Schema(description = "聊天请求")
public class ChatRequest {
    
    @Schema(description = "用户问题", example = "公司的年假政策是什么？", required = true)
    private String question;
    
    @Schema(description = "知识库ID列表", example = "[1, 2]", required = true)
    private List<Long> kbIds;
    
    @Schema(description = "会话ID，为空时创建新会话", example = "session-abc123")
    private String sessionId;
}