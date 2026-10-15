package com.rhs.rag.controller;


import com.rhs.rag.dto.ApiResponse;
import com.rhs.rag.entity.KbDocument;
import com.rhs.rag.service.DocumentUpdateService;
import com.rhs.rag.service.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档更新管理接口
 */
@Tag(name = "文档更新管理", description = "文档内容替换和强制重建索引等操作")
@RestController
@RequestMapping("/api/v1/kb")
@RequiredArgsConstructor
public class DocumentUpdateController {

    private final PermissionService permissionService;
    private final DocumentUpdateService documentUpdateService;

    @Operation(
        summary = "替换文档内容", 
        description = "上传新文件替换文档内容，保持文档ID不变。适用于文档版本更新、内容修正等场景。替换后会自动触发重新索引。"
    )
    @PutMapping("/{kbId}/documents/{docId}/content")
    public ApiResponse<KbDocument> replaceContent(
            @Parameter(description = "知识库ID", required = true, example = "1") @PathVariable Long kbId,
            @Parameter(description = "文档ID", required = true, example = "1") @PathVariable Long docId,
            @Parameter(description = "新文档文件", required = true) @RequestParam("file") MultipartFile file) {
        permissionService.requireWrite(kbId);
        return ApiResponse.ok(documentUpdateService.replaceDocument(docId, file));
    }

    @Operation(
        summary = "强制重建索引", 
        description = "文档内容未变，但索引参数或策略发生变化时强制重建索引。适用场景：修改切块参数、更换Embedding模型、升级文档解析器、索引失败重试等。"
    )
    @PostMapping("/{kbId}/documents/{docId}/reindex-force")
    public ApiResponse<Void> forceReindex(
            @Parameter(description = "知识库ID", required = true, example = "1") @PathVariable Long kbId,
            @Parameter(description = "文档ID", required = true, example = "1") @PathVariable Long docId) {
        permissionService.requireWrite(kbId);
        documentUpdateService.forceReindexAndSubmit(docId);
        return ApiResponse.ok(null);
    }
}