package com.rhs.rag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "文档上传响应")
public class DocumentUploadResponse {
    
    @Schema(description = "文档ID", example = "1")
    private Long docId;
    
    @Schema(description = "文件名", example = "技术方案.pdf")
    private String fileName;
    
    @Schema(description = "文档状态", example = "PENDING", allowableValues = {"PENDING", "PROCESSING", "DONE", "FAILED"})
    private String status;
    
    @Schema(description = "提示信息", example = "文档已上传，正在后台索引，请通过 /status 接口查询进度")
    private String message;

    public static DocumentUploadResponse submitted(Long docId, String fileName) {
        DocumentUploadResponse r = new DocumentUploadResponse();
        r.setDocId(docId);
        r.setFileName(fileName);
        r.setStatus("PENDING");
        r.setMessage("文档已上传，正在后台索引，请通过 /status 接口查询进度");
        return r;
    }
}