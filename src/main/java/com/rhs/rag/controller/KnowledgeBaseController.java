package com.rhs.rag.controller;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rhs.rag.dto.*;
import com.rhs.rag.entity.IndexTask;
import com.rhs.rag.entity.KbDocument;
import com.rhs.rag.entity.KnowledgeBase;
import com.rhs.rag.mapper.IndexTaskMapper;
import com.rhs.rag.mapper.KbDocumentMapper;
import com.rhs.rag.service.KnowledgeBaseService;
import com.rhs.rag.service.PermissionService;
import com.rhs.rag.service.loader.MinioStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 知识库管理接口
 */
@Tag(name = "知识库管理", description = "知识库的创建、查询、文档管理等操作")
@RestController
@RequestMapping("/api/v1/kb")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final PermissionService permissionService;
    private final KnowledgeBaseService kbService;
    private final KbDocumentMapper kbDocumentMapper;
    private final IndexTaskMapper indexTaskMapper;
    private final MinioStorageService minioService;

    @Operation(summary = "查询知识库列表", description = "查询当前用户可访问的知识库列表，包含权限级别信息")
    @GetMapping
    public ApiResponse<List<KnowledgeBaseVO>> list() {
        return ApiResponse.ok(kbService.listAccessible());
    }

    @Operation(summary = "创建知识库", description = "创建一个新的知识库，创建者自动获得管理员权限")
    @PostMapping
    public ApiResponse<KnowledgeBase> create(
            @Parameter(description = "知识库创建请求") @RequestBody KnowledgeBaseCreateRequest req) {
        return ApiResponse.ok(kbService.create(req));
    }

    @Operation(summary = "查询文档列表", description = "查询指定知识库下的所有文档列表")
    @GetMapping("/{kbId}/documents")
    public ApiResponse<List<KbDocument>> listDocuments(
            @Parameter(description = "知识库ID", required = true) @PathVariable Long kbId) {
        permissionService.requireRead(kbId);
        List<KbDocument> documents = kbDocumentMapper.selectList(new LambdaQueryWrapper<KbDocument>()
                .eq(KbDocument::getKbId, kbId)
                .eq(KbDocument::getIsDeleted, false));
        return ApiResponse.ok(documents);
    }

    @Operation(summary = "上传文档", description = "上传文档到指定知识库，支持PDF、DOCX、MD、TXT格式，上传后自动提交索引任务")
    @PostMapping("/{kbId}/documents")
    public ApiResponse<DocumentUploadResponse> upload(
            @Parameter(description = "知识库ID", required = true) @PathVariable Long kbId,
            @Parameter(description = "上传的文件", required = true) @RequestParam("file") MultipartFile file) {
        permissionService.requireWrite(kbId);
        KbDocument doc = kbService.uploadDocument(kbId, file);
        return ApiResponse.ok(DocumentUploadResponse.submitted(doc.getId(), doc.getFileName()));
    }

    @Operation(summary = "查询索引状态", description = "查询文档的索引进度，用于前端轮询")
    @GetMapping("/{kbId}/documents/{docId}/status")
    public ApiResponse<IndexStatusResponse> getStatus(
            @Parameter(description = "知识库ID", required = true) @PathVariable Long kbId,
            @Parameter(description = "文档ID", required = true) @PathVariable Long docId) {
        permissionService.requireRead(kbId);

        KbDocument document = kbDocumentMapper.selectById(docId);
        if (document == null){
            throw new RuntimeException("文档不存在");
        }

        // 查最新的索引任务（可能有重试）
        IndexTask latestTask = indexTaskMapper.selectOne(new LambdaQueryWrapper<IndexTask>()
                .eq(IndexTask::getDocId,docId)
                .orderByDesc(IndexTask::getCreatedAt)
                .last("LIMIT 1"));

        IndexStatusResponse resp = new IndexStatusResponse();
        resp.setDocId(document.getId());
        resp.setFileName(document.getFileName());
        resp.setStatus(document.getStatus().name());
        resp.setErrorMsg(document.getErrorMsg());
        resp.setChunkCount(document.getChunkCount());
        resp.setTokenCount(document.getTokenCount());
        resp.setIndexedAt(document.getIndexedAt() != null ? document.getIndexedAt().toString() : null);
        resp.setRetryCount(latestTask != null ? latestTask.getRetryCount() : 0);
        return ApiResponse.ok(resp);
    }

    @Operation(summary = "删除文档", description = "删除指定文档，会同时删除向量数据和MinIO中的原始文件")
    @DeleteMapping("/{kbId}/documents/{docId}")
    public ApiResponse<Void> deleteDocument(
            @Parameter(description = "知识库ID", required = true) @PathVariable Long kbId,
            @Parameter(description = "文档ID", required = true) @PathVariable Long docId) {
        permissionService.requireWrite(kbId);
        kbService.deleteDocument(docId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "下载文档", description = "下载知识库中的原始文件")
    @GetMapping("/{kbId}/documents/{docId}/download")
    public ResponseEntity<byte[]> download(
            @Parameter(description = "知识库ID", required = true) @PathVariable Long kbId,
            @Parameter(description = "文档ID", required = true) @PathVariable Long docId) {
        permissionService.requireRead(kbId);
        KbDocument doc = kbDocumentMapper.selectById(docId);
        if (doc == null){
            throw new RuntimeException("文档不存在");
        }
        byte[] content = minioService.download(doc.getMinioPath());
        String encodedName = URLEncoder.encode(doc.getFileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(content);
    }

    @Operation(summary = "重建索引", description = "重新对文档进行分块和向量化，适用于修改切块参数、更换Embedding模型等场景")
    @PostMapping("/{kbId}/documents/{docId}/reindex")
    public ApiResponse<String> reindex(
            @Parameter(description = "知识库ID", required = true) @PathVariable Long kbId,
            @Parameter(description = "文档ID", required = true) @PathVariable Long docId) {
        permissionService.requireWrite(kbId);
        kbService.reindex(docId);
        return ApiResponse.ok("重建索引任务已提交，请通过 /status 接口查询进度");
    }
}