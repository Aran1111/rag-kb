package com.rhs.rag.service;


import com.rhs.rag.entity.DocChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 升级版 RagQueryService：使用混合检索代替纯向量检索。
 * 后续 Reranker 节会在此基础上继续升级。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RagQueryServiceV2 {

    private final HybridRetrieverService hybridRetriever;
    private final ChatClient chatClient;

    @Value("${rag.retrieval.return-top-n:5}")
    private int returnTopN;

    public String query(String question, List<Long> kbIds) {
        // Step 1：混合检索
        List<HybridRetrieverService.ScoredChunk> scoredChunks =
                hybridRetriever.retrieveWithPermissionCheck(question, kbIds, returnTopN);

        if (scoredChunks.isEmpty()) {
            return buildNotFoundResponse();
        }

        // Step 2：生成回答
        List<DocChunk> chunks = scoredChunks.stream()
                .map(HybridRetrieverService.ScoredChunk::chunk)
                .collect(Collectors.toList());

        return generateAnswer(question, chunks);
    }

    private String generateAnswer(String question, List<DocChunk> chunks) {
        String context = buildContext(chunks);
        String systemPrompt = buildSystemPrompt(context);

        return chatClient.prompt()
                .system(systemPrompt)
                .user(question)
                .call()
                .content();
    }

    private String buildContext(List<DocChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            DocChunk c = chunks.get(i);
            sb.append("[参考").append(i + 1).append("]");
            if (c.getSectionTitle() != null) sb.append(" ").append(c.getSectionTitle());
            sb.append("\n").append(c.getContent()).append("\n\n");
        }
        return sb.toString().strip();
    }

    private String buildSystemPrompt(String context) {
        return """
                你是企业内部知识库的智能助手。你的工作是根据提供的参考文档内容，准确回答员工的问题。
                
                重要规则：
                1. 只根据提供的【参考内容】回答问题，不要使用自己的知识进行推测或补充
                2. 如果参考内容不足以回答问题，明确告诉用户"在知识库中未找到相关信息"，并建议用户联系相关部门
                3. 回答要准确、简洁，用中文回答
                4. 如果参考内容涉及多个文档，综合各文档回答
                5. 禁止编造不在参考内容中的信息
                
                参考内容如下：
                ---
                %s
                ---
                """.formatted(context);
    }

    private String buildNotFoundResponse() {
        return "在您选择的知识库中未找到与该问题相关的内容。建议您：\n" +
               "1. 确认问题是否与知识库主题相关\n" +
               "2. 尝试用不同关键词提问\n" +
               "3. 联系相关部门获取准确信息";
    }
}