package com.rhs.rag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "索引状态响应")
public class IndexStatusResponse {
    
    @Schema(description = "文档ID", example = "1")
    private Long docId;
    
    @Schema(description = "文件名", example = "技术方案.pdf")
    private String fileName;
    
    @Schema(description = "文档状态", example = "DONE", allowableValues = {"PENDING", "PROCESSING", "DONE", "FAILED"})
    private String status;
    
    @Schema(description = "错误信息，索引失败时返回", example = "文件解析失败")
    private String errorMsg;
    
    @Schema(description = "分块数量", example = "15")
    private Integer chunkCount;
    
    @Schema(description = "Token数量", example = "5000")
    private Integer tokenCount;
    
    @Schema(description = "索引完成时间", example = "2024-01-01T10:30:00")
    private String indexedAt;
    
    @Schema(description = "重试次数", example = "0")
    private Integer retryCount;
}