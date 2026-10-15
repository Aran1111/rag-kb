package com.rhs.rag.controller;


import com.rhs.rag.dto.ApiResponse;
import com.rhs.rag.dto.RagQueryRequest;
import com.rhs.rag.dto.RagResponse;
import com.rhs.rag.service.FullRagPipeline;
import com.rhs.rag.service.RagQueryService;
import com.rhs.rag.service.RagQueryServiceV2;
import com.rhs.rag.service.RagQueryServiceV3;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * RAG智能问答接口
 */
@Tag(name = "RAG智能问答", description = "基于知识库的智能问答")
@RestController
@RequestMapping("/api/v1/rag")
@RequiredArgsConstructor
public class RagQueryController {

    private final RagQueryService ragQueryService;
    private final RagQueryServiceV2 ragQueryServicev2;
    private final RagQueryServiceV3 ragQueryServicev3;
    private final FullRagPipeline ragQueryServicev4;

    @Operation(
        summary = "智能问答（V1 MVP版本）",
        description = "基于向量检索的智能问答，支持多知识库联合检索"
    )
    @PostMapping("/query")
    public ApiResponse<String> query(@RequestBody RagQueryRequest req) {
        return ApiResponse.ok(ragQueryService.query(req.getQuestion(), req.getKbIds()));
    }

    @Operation(
        summary = "智能问答（V2 混合检索版）",
        description = "混合检索，检索准确率更高，生产环境推荐使用"
    )
    @PostMapping("/queryV2")
    public ApiResponse<String> queryV2(@RequestBody RagQueryRequest req) {
        return ApiResponse.ok(ragQueryServicev2.query(req.getQuestion(), req.getKbIds()));
    }

    @Operation(
            summary = "智能问答（V3 混合检索 + hyde版）",
            description = "用户问题hyde+混合检索，检索准确率更高"
    )
    @PostMapping("/queryV3")
    public ApiResponse<String> queryV3(@RequestBody RagQueryRequest req) {
        return ApiResponse.ok(ragQueryServicev3.query(req.getQuestion(), req.getKbIds()));
    }

    @Operation(
            summary = "智能问答（V4 完整链路版本）",
            description = "用户问题hyde+混合检索 + 精排 + 上下文裁剪，检索准确率更高，生产环境推荐使用"
    )
    @PostMapping("/queryV4")
    public ApiResponse<RagResponse> queryV4(@RequestBody RagQueryRequest req) {
        return ApiResponse.ok(ragQueryServicev4.query(req.getQuestion(), req.getKbIds()));
    }
}