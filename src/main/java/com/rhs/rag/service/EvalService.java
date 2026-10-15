package com.rhs.rag.service;


import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rhs.rag.dto.EvalReport;
import com.rhs.rag.dto.RagResponse;
import com.rhs.rag.entity.EvalDataset;
import com.rhs.rag.entity.EvalResult;
import com.rhs.rag.exception.BizException;
import com.rhs.rag.mapper.EvalDatasetMapper;
import com.rhs.rag.mapper.EvalResultMapper;
import com.rhs.rag.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EvalService {

    private final EvalDatasetMapper evalDatasetMapper;
    private final EvalResultMapper evalResultMapper;
    private final EnhancedRetrieverService retriever;
    private final RerankerService rerankerService;
    private final ConfidenceFilter confidenceFilter;
    private final HallucinationChecker hallucinationChecker;
    private final StreamingRagService ragService;

    /**
     * 检索质量评估：对每个标准问题跑完整链路（HyDE 检索 → Rerank 精排），
     * 看 "期望命中的 chunk" 有没有被召回、排在第几位，汇总成 HitRate（命中率） 和 MRR（平均倒数排名）。
     * 衡量的是：检索器能不能把正确答案捞上来、还排得靠前。
     *
     * 回答忠实性评估：跑一次完整问答（syncQuery），用 HallucinationChecker 检查回答与检索到的上下文是否一致，得出平均忠实性分数。衡量的是：生成阶段有没有幻觉。
     * 版本 A/B 对比：compareVersions 按 evalVersion 聚合历史结果，把不同配置下的指标放一起对比。
     */

    /**
     * 评测时精排保留的 chunk 数。可配置是为了做 A/B 对比：
     * 设成比候选总量还大的数（如 999）可跳过精排，得到"纯混合检索"的基线指标；
     * 默认 10 则走 Rerank 精排，两个版本跑出来的 HitRate/MRR 差值就是精排的净收益。
     */
    @Value("${eval.rerank-top-n:10}")
    private int rerankTopN;

    /**
     * 运行完整评估流水线。
     *
     * @param kbId        知识库 ID
     * @param evalVersion 评估版本标识（如 v1_chunk512_hybrid_reranker）
     * @return 评估摘要报告
     */
    public EvalReport runEvaluation(Long kbId, String evalVersion) {
        if (!"ADMIN".equals(UserContext.getRole())){
            BizException.forbidden("仅管理员进行评估");
        }
        List<EvalDataset> questions = evalDatasetMapper.selectList(new LambdaQueryWrapper<EvalDataset>()
                .eq(EvalDataset::getKbId,kbId));
        if (questions.isEmpty()) {
            throw new RuntimeException("知识库 " + kbId + " 没有评估数据集，请先录入标准问题");
        }

        log.info("[Eval] 开始评估：kbId={}，version={}，问题数={}", kbId, evalVersion, questions.size());

        List<EvalResult> results = new ArrayList<>();
        int hits = 0;
        double mrr = 0.0;
        double totalFaithfulness = 0.0;
        int evalCount = 0;

        for (EvalDataset question : questions) {
            try {
                EvalResult result = evaluateOne(question, kbId, evalVersion);
                results.add(result);

                if (result.getHit()) hits++;
                if (result.getRank() != null && result.getRank() > 0) {
                    mrr += 1.0 / result.getRank();
                }
                if (result.getFaithfulness() != null) {
                    totalFaithfulness += result.getFaithfulness();
                    evalCount++;
                }

            } catch (Exception e) {
                log.error("[Eval] 问题评估失败：questionId={}，error={}", question.getId(), e.getMessage());
            }
        }

        // 批量保存评估结果
        evalResultMapper.insert(results);

        double hitRate = questions.isEmpty() ? 0 : (double) hits / questions.size();
        double mrrScore = questions.isEmpty() ? 0 : mrr / questions.size();
        double avgFaithfulness = evalCount == 0 ? 0 : totalFaithfulness / evalCount;

        EvalReport report = EvalReport.builder()
                .kbId(kbId)
                .evalVersion(evalVersion)
                .totalQuestions(questions.size())
                .hitCount(hits)
                .hitRate(hitRate)
                .mrr(mrrScore)
                .avgFaithfulness(avgFaithfulness)
                .evalAt(LocalDateTime.now())
                .build();

        log.info("[Eval] 评估完成：hitRate={}%，MRR={}，faithfulness={}",
                String.format("%.2f", hitRate * 100),
                String.format("%.4f", mrrScore),
                String.format("%.4f", avgFaithfulness));

        return report;
    }

    private EvalResult evaluateOne(EvalDataset question, Long kbId, String evalVersion) {
        // 执行检索
        List<HybridRetrieverService.ScoredChunk> candidates =
                retriever.retrieveWithHyde(question.getQuestion(), List.of(kbId), 20);
        List<HybridRetrieverService.ScoredChunk> reranked =
                rerankerService.rerank(question.getQuestion(), candidates, rerankTopN);

        // 计算 Hit Rate 和 MRR
        Long[] expectedChunkIds = question.getExpectedChunkIds();
        boolean hit = false;
        int rank = 0;

        if (expectedChunkIds != null && expectedChunkIds.length > 0) {
            Set<Long> expected = Set.of(expectedChunkIds);
            // MRR（平均倒数排名）：每个问题只取第一个正确答案出现的位置的倒数（1/rank）
            for (int i = 0; i < reranked.size(); i++) {
                if (expected.contains(reranked.get(i).id())) {
                    hit = true;
                    rank = i + 1;  // 1-based
                    break;
                }
            }
        }

        // 生成回答并评估忠实性（抽样评估，降低成本）
        // 忠实性是考 "有没有照着资料说" 回答 vs LLM 实际参考的资料" 的一致性。
        String actualAnswer = null;
        Double faithfulness = null;

        if (question.getExpectedAnswer() != null) {
            RagResponse response = ragService.syncQuery(
                    question.getQuestion(), List.of(kbId), "eval-session");
            // 大模型回答的问题
            actualAnswer = response.getAnswer();

            // 忠实性检测（每次评估都跑，但仅限标准问题集）
            // 忠实性是考 "有没有照着资料说"
            String context = candidates.stream()
                    .limit(5)
                    .map(HybridRetrieverService.ScoredChunk::content)
                    .collect(Collectors.joining("\n\n"));

            HallucinationChecker.FaithfulnessResult faithResult =
                    hallucinationChecker.check(question.getQuestion(), actualAnswer, context);
            faithfulness = faithResult.score();
        }

        EvalResult result = new EvalResult();
        result.setDatasetId(question.getId());
        result.setEvalVersion(evalVersion);
        result.setHit(hit);
        result.setRank(rank > 0 ? rank : null);
        result.setActualAnswer(actualAnswer);
        result.setFaithfulness(faithfulness);
        result.setEvalAt(LocalDateTime.now());

        return result;
    }

    /**
     * 对比不同评估版本的指标，生成对比报告。
     */
    public List<EvalReport> compareVersions(Long kbId) {
        // 从数据库查历史评估结果，按版本聚合
        return evalResultMapper.aggregateByVersion(kbId);
    }
}