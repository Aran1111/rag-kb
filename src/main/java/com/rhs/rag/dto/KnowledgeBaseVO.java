package com.rhs.rag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "知识库视图对象")
public class KnowledgeBaseVO {
    
    @Schema(description = "知识库ID", example = "1")
    private Long id;
    
    @Schema(description = "知识库名称", example = "技术文档库")
    private String name;
    
    @Schema(description = "知识库描述", example = "存储技术团队的所有技术文档")
    private String description;
    
    @Schema(description = "所属部门ID", example = "dept-001")
    private String departmentId;
    
    @Schema(description = "是否公开", example = "false")
    private Boolean isPublic;
    
    @Schema(description = "创建者用户ID", example = "1001")
    private Long createdBy;
    
    @Schema(description = "创建时间", example = "2024-01-01T10:00:00")
    private LocalDateTime createdAt;
    
    @Schema(description = "当前用户对该知识库的权限", example = "ADMIN", allowableValues = {"ADMIN", "WRITE", "READ"})
    private String permission;
}