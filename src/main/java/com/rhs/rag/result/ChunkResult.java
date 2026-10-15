package com.rhs.rag.result;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ChunkResult {

    /** 分块序号 */
    private int chunkIndex;
    /** 分块内容 */
    private String content;
    /** 分块来自文档第几页 */
    private int pageNum;
    /** 所在章节标题 */
    private String sectionTitle;
    /** 估算的 Token 数 */
    private int estimatedTokens;
}
