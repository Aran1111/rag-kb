package com.rhs.rag.service.loader.impl.splitter;

import com.rhs.rag.config.ChunkConfig;
import com.rhs.rag.result.ChunkResult;
import com.rhs.rag.result.ParseResult;
import com.rhs.rag.service.loader.ChunkSplitter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class SlidingWindowChunkSplitter implements ChunkSplitter {
    @Override
    public List<ChunkResult> split(ParseResult result, ChunkConfig config) {
        ArrayList<ChunkResult> results = new ArrayList<>();
        int chunkIndex = 0;

        for (ParseResult.PageContent page : result.getPages()) {
            // 拿到每页的文本内容
            String text = page.getText();
            if (text == null && text.isBlank()) continue;

            List<String> pageChunks = splitText(text,config.getChunkSize(), config.getChunkOverlap());

            for (String pageChunk : pageChunks) {
                results.add(ChunkResult.builder()
                        .chunkIndex(chunkIndex++)
                        .pageNum(page.getPageNum())
                        .sectionTitle(page.getSectionTitle())
                        .content(pageChunk)
                        .estimatedTokens(estimateTokens(pageChunk))
                        .build());
            }
        }
        log.debug("[分块] 文档分块完成，共{}块，avgSize={}字符",
                results.size(),
                results.isEmpty() ? 0 : results.stream()
                        .mapToInt(c -> c.getContent().length()).average().orElse(0));

        return results;
    }

    private List<String> splitText(String text, int chunkSize, int chunkOverlap) {
        List<String> result = new ArrayList<>();
        int start = 0;

        while (start < text.length()){
            int end = Math.min(start + chunkSize, text.length());

            if (end < text.length()){
                // 表示这次分块没超出字符串长度
                end = findGoodBreakPoint(text, end, chunkOverlap);
            }

            String chunk = text.substring(start, end).strip();
            if (!chunk.isBlank()) {
                result.add(chunk);
            }

            // 下一次分块开始位置
            int nextStart = end - chunkOverlap;

            if (nextStart <= start) {
                nextStart = end;
            }
            start = nextStart;
        }

        return result;
    }

    /**
     * 从 position 向前找到一个好的断点（段落 > 句号 > 逗号 > 空格）。
     *      * 最多回退 100 字符，找不到就直接断。
     * @param text
     * @param position
     * @param overlap
     * @return
     */
    private int findGoodBreakPoint(String text, int position, int overlap) {
        int searchRange = Math.min(100, position - overlap);  // 不能回退太多

        // 优先级：段落换行 > 句号/问号/感叹号 > 分号/逗号 > 空格
        String[] breakChars = {"\n\n", "\n", "。", "！", "？", "；", "，", " "};

        for (String breakChar : breakChars) {
            int idx = text.lastIndexOf(breakChar, position);
            if (idx > position - searchRange && idx > 0) {
                return idx + breakChar.length();
            }
        }
        return position;  // 找不到好的断点，直接截断
    }

    private int estimateTokens(String pageChunk) {
        if (pageChunk == null) return 0;
        int chineseChars = 0;
        int otherChars = 0;
        for (char c : pageChunk.toCharArray()) {
            if (c >= '\u4e00' && c <= '\u9fff') {
                chineseChars++;
            } else if (!Character.isWhitespace(c)) {
                otherChars++;
            }
        }
        return (int) (chineseChars * 1.5 + otherChars * 0.3);
    }
}
