package com.rhs.rag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "知识库创建请求")
public class KnowledgeBaseCreateRequest {
    
    @Schema(description = "知识库名称", example = "技术文档库", required = true)
    private String name;
    
    @Schema(description = "知识库描述", example = "存储技术团队的所有技术文档")
    private String description;
    
    @Schema(description = "所属部门ID", example = "dept-001")
    private String departmentId;
    
    @Schema(description = "是否公开，默认false", example = "false")
    private Boolean isPublic = false;
}