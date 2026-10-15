package com.rhs.rag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "RAG查询请求")
public class RagQueryRequest {
    
    @Schema(description = "用户问题", example = "公司的年假政策是什么？", required = true)
    private String question;
    
    @Schema(description = "知识库ID列表，指定在哪些知识库中检索", example = "[1, 2]", required = true)
    private List<Long> kbIds;
}