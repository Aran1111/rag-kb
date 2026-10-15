package com.rhs.rag.service;


import com.rhs.rag.dto.RagResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FullRagPipeline {

    private final EnhancedRetrieverService enhancedRetriever;
    private final RerankerService rerankerService;
    private final ConfidenceFilter confidenceFilter;
    private final ContextTrimmerService contextTrimmer;
    private final SourceBuilder sourceBuilder;
    // 防幻觉校验
    private final HallucinationChecker hallucinationChecker;
    private final ChatClient chatClient;

    @Value("${reranker.top-n:5}")
    private int rerankerTopN;

    public RagResponse query(String question, List<Long> kbIds) {
        long pipelineStart = System.currentTimeMillis();

        // Step 1：增强检索（混合检索 + HyDE）
        List<HybridRetrieverService.ScoredChunk> candidates =
                enhancedRetriever.retrieveWithHyde(question, kbIds, 20);

        if (candidates.isEmpty()) {
            return RagResponse.notFound();
        }

        // Step 2：Reranker 精排
        List<HybridRetrieverService.ScoredChunk> reranked =
                rerankerService.rerank(question, candidates, rerankerTopN);

        // Step 3：置信度过滤
        List<HybridRetrieverService.ScoredChunk> filtered = confidenceFilter.filter(reranked);

        if (filtered.isEmpty()) {
            return RagResponse.notFound();
        }

        // Step 4：上下文裁剪（控制 Token 预算）
        List<HybridRetrieverService.ScoredChunk> trimmed = contextTrimmer.trim(filtered);

        // Step 5：构建带引用编号的 context + 用 RagPromptTemplate 生成 System Prompt
        String context = buildContext(trimmed);
        String answer = generateAnswer(question, context, trimmed.size());

        // Step 6：用 SourceBuilder 解析引用标注，关联到文档信息
        List<RagResponse.Source> sources = sourceBuilder.buildSources(answer, trimmed);

        // Step 7：幻觉检测（抽样，每 5 次查询跑 1 次，避免增加太多成本）
        if (System.currentTimeMillis() % 5 == 0) {
            var faithResult = hallucinationChecker.check(question, answer, context);
            if (!faithResult.isFaithful()) {
                log.warn("[FullRagPipeline] 幻觉检测不通过：score={}，reason={}",
                        faithResult.score(), faithResult.reason());
            }
        }

        long elapsed = System.currentTimeMillis() - pipelineStart;
        log.info("[FullRagPipeline] 完成：question={}，elapsed={}ms，sources={}",
                question.substring(0, Math.min(30, question.length())), elapsed, sources.size());

        return RagResponse.builder()
                .answer(answer)
                .sources(sources)
                .latencyMs((int) elapsed)
                .build();
    }

    private List<RagResponse.Source> buildSources(List<HybridRetrieverService.ScoredChunk> chunks) {
        return chunks.stream()
                .map(sc -> RagResponse.Source.builder()
                        .chunkId(sc.id())
                        .docId(sc.chunk().getDocId())
                        .pageNum(sc.chunk().getPageNum())
                        .sectionTitle(sc.chunk().getSectionTitle())
                        .excerpt(sc.content().substring(0,Math.min(200,sc.content().length())))
                        .score(sc.score())
                        .build())
                .collect(Collectors.toList());
    }

    private String buildContext(List<HybridRetrieverService.ScoredChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            var sc = chunks.get(i);
            sb.append("[参考").append(i + 1).append("]");
            if (sc.chunk().getSectionTitle() != null) {
                sb.append(" ").append(sc.chunk().getSectionTitle());
            }
            sb.append("\n").append(sc.content()).append("\n\n");
        }
        return sb.toString().strip();
    }

    private String generateAnswer(String question, String context, int chunkCount) {
        String systemPrompt = RagPromptTemplate.buildSystemPrompt(context, chunkCount);
//        String systemPrompt = """
//                你是企业内部知识库的智能助手。你的工作是根据提供的参考文档内容，准确回答员工的问题。
//
//                重要规则：
//                1. 只根据提供的【参考内容】回答问题，不要使用自己的知识进行推测或补充
//                2. 如果参考内容不足以回答问题，明确告诉用户"在知识库中未找到相关信息"，并建议用户联系相关部门
//                3. 回答要准确、简洁，用中文回答
//                4. 如果参考内容涉及多个文档，综合各文档回答
//                5. 禁止编造不在参考内容中的信息
//
//                参考内容如下：
//                ---
//                %s
//                ---
//                """.formatted(context);
        return chatClient.prompt()
                .system(systemPrompt)
                .user(question)
                .call()
                .content();
    }
}