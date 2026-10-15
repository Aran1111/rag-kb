package com.rhs.rag.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rhs.rag.dto.ApiResponse;
import com.rhs.rag.dto.EvalReport;
import com.rhs.rag.entity.DocChunk;
import com.rhs.rag.entity.EvalDataset;
import com.rhs.rag.mapper.DocChunkMapper;
import com.rhs.rag.mapper.EvalDatasetMapper;
import com.rhs.rag.security.UserContext;
import com.rhs.rag.service.EvalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "评测管理", description = "RAG 检索/生成质量评估：触发评估、历史版本对比、评估数据集管理与 Chunk 标注")
@RestController
@RequestMapping("/api/v1/eval")
@RequiredArgsConstructor
public class EvalController {

    private final EvalService evalService;
    private final EvalDatasetMapper evalDatasetMapper;
    private final DocChunkMapper docChunkMapper;

    /**
     * 触发评估（管理员专用）
     */
    @Operation(summary = "触发评估（仅管理员）",
            description = "对指定知识库的标准问题集运行完整评估流水线（HyDE检索 → Rerank精排 → HitRate/MRR 与忠实性），结果按评估版本存储，可用于 A/B 对比")
    @PostMapping("/{kbId}/run")
    public ApiResponse<EvalReport> runEval(
            @Parameter(description = "知识库 ID", required = true, example = "1")
            @PathVariable Long kbId,
            @Parameter(description = "评估版本标识（如 v1_chunk512_hybrid_reranker），用于 A/B 分组对比", example = "latest")
            @RequestParam(defaultValue = "latest") String version) {
        EvalReport report = evalService.runEvaluation(kbId, version);
        return ApiResponse.ok(report);
    }

    /**
     * 查看历史评估对比
     */
    @Operation(summary = "查看历史评估对比",
            description = "按评估版本聚合指定知识库的历史评估结果，返回各版本的 HitRate、MRR、忠实性指标")
    @GetMapping("/{kbId}/history")
    public ApiResponse<List<EvalReport>> getHistory(
            @Parameter(description = "知识库 ID", required = true, example = "1")
            @PathVariable Long kbId) {
        return ApiResponse.ok(evalService.compareVersions(kbId));
    }

    // ==================== 评估数据集管理 ====================

    /**
     * 查询知识库的评估数据集
     */
    @Operation(summary = "查询评估数据集",
            description = "返回指定知识库下录入的标准问题集（问题、期望答案、期望命中的 chunk）")
    @GetMapping("/{kbId}/dataset")
    public ApiResponse<List<EvalDataset>> listDataset(
            @Parameter(description = "知识库 ID", required = true, example = "1")
            @PathVariable Long kbId) {
        return ApiResponse.ok(evalDatasetMapper.selectList(new LambdaQueryWrapper<EvalDataset>()
                .eq(EvalDataset::getKbId, kbId)));
    }

    /**
     * 新增评估问题
     */
    @Operation(summary = "新增评估问题",
            description = "向指定知识库的评估数据集添加一条标准问题（含期望答案与期望命中的 chunk）")
    @PostMapping("/{kbId}/dataset")
    public ApiResponse<Integer> addQuestion(
            @Parameter(description = "知识库 ID", required = true, example = "1")
            @PathVariable Long kbId,
            @RequestBody EvalDatasetRequest req) {
        EvalDataset item = new EvalDataset();
        item.setKbId(kbId);
        item.setQuestion(req.getQuestion());
        item.setExpectedAnswer(req.getExpectedAnswer());
        item.setExpectedChunkIds(req.getExpectedChunkIds());
        item.setCreatedBy(UserContext.getUserId());
        return ApiResponse.ok(evalDatasetMapper.insert(item));
    }

    /**
     * 更新评估问题（含回填 expectedChunkIds）
     */
    @Operation(summary = "更新评估问题",
            description = "更新评估数据集中的标准问题，字段为空则不修改；可用于回填 expectedChunkIds")
    @PutMapping("/{kbId}/dataset/{id}")
    public ApiResponse<Integer> updateQuestion(
            @Parameter(description = "知识库 ID", required = true, example = "1")
            @PathVariable Long kbId,
            @Parameter(description = "评估问题 ID", required = true, example = "1")
            @PathVariable Long id,
            @RequestBody EvalDatasetRequest req) {
        EvalDataset item = evalDatasetMapper.selectById(id);
        if (item == null){
            throw new RuntimeException("评估数据不存在");
        }
        if (req.getQuestion() != null) item.setQuestion(req.getQuestion());
        if (req.getExpectedAnswer() != null) item.setExpectedAnswer(req.getExpectedAnswer());
        if (req.getExpectedChunkIds() != null) item.setExpectedChunkIds(req.getExpectedChunkIds());
        return ApiResponse.ok(evalDatasetMapper.updateById(item));
    }

    /**
     * 删除评估问题
     */
    @Operation(summary = "删除评估问题",
            description = "从评估数据集中删除一条标准问题")
    @DeleteMapping("/{kbId}/dataset/{id}")
    public ApiResponse<Void> deleteQuestion(
            @Parameter(description = "知识库 ID", required = true, example = "1")
            @PathVariable Long kbId,
            @Parameter(description = "评估问题 ID", required = true, example = "1")
            @PathVariable Long id) {
        evalDatasetMapper.deleteById(id);
        return ApiResponse.ok(null);
    }

    // ==================== Chunk 查询（用于标注 expectedChunkIds） ====================

    /**
     * 查询知识库下的所有 Chunk（只返回 id、docId、chunkIndex 和内容摘要）
     */
    @Operation(summary = "查询知识库 Chunk",
            description = "查询指定知识库下的所有 Chunk，返回 id、docId、chunkIndex、内容摘要与 token 数，用于标注 expectedChunkIds")
    @GetMapping("/{kbId}/chunks")
    public ApiResponse<List<ChunkSummary>> listChunks(
            @Parameter(description = "知识库 ID", required = true, example = "1")
            @PathVariable Long kbId) {
        List<DocChunk> chunks = docChunkMapper.selectList(new LambdaQueryWrapper<DocChunk>()
                .eq(DocChunk::getKbId, kbId));
        List<ChunkSummary> summaries = chunks.stream().map(c -> {
            ChunkSummary s = new ChunkSummary();
            s.setId(c.getId());
            s.setDocId(c.getDocId());
            s.setChunkIndex(c.getChunkIndex());
            s.setContent(c.getContent().length() > 200
                    ? c.getContent().substring(0, 200) + "..."
                    : c.getContent());
            s.setTokenCount(c.getTokenCount());
            return s;
        }).toList();
        return ApiResponse.ok(summaries);
    }

    @Data
    @Schema(description = "评估问题录入请求")
    public static class EvalDatasetRequest {
        @Schema(description = "标准问题", example = "公司的年假政策是什么？", requiredMode = Schema.RequiredMode.REQUIRED)
        private String question;

        @Schema(description = "期望答案（用于忠实性评估）", example = "入职满一年后可享受带薪年假...")
        private String expectedAnswer;

        @Schema(description = "期望命中的 Chunk ID 列表（用于 HitRate/MRR）", example = "[1001, 1002]")
        private Long[] expectedChunkIds;
    }

    @Data
    @Schema(description = "Chunk 摘要信息")
    public static class ChunkSummary {
        @Schema(description = "Chunk ID", example = "1001")
        private Long id;

        @Schema(description = "所属文档 ID", example = "1")
        private Long docId;

        @Schema(description = "Chunk 在文档内的序号", example = "3")
        private Integer chunkIndex;

        @Schema(description = "内容摘要（前200字）")
        private String content;

        @Schema(description = "Token 数", example = "512")
        private Integer tokenCount;
    }
}