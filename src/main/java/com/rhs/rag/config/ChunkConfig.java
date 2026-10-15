package com.rhs.rag.config;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChunkConfig {

    /** 分块字符大小 */
    @Builder.Default
    private int chunkSize = 512;
    /** 相邻块重叠字符 */
    @Builder.Default
    private int chunkOverlap = 60;
    /** 是否启用结构感知分块（按段落/标题断点） */
    @Builder.Default
    private boolean structureAware = true;

    public static ChunkConfig defaultConfig(){
        return ChunkConfig.builder().build();
    }
}
