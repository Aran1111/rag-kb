package com.rhs.rag.service.loader;

import com.rhs.rag.config.ChunkConfig;
import com.rhs.rag.result.ChunkResult;
import com.rhs.rag.result.ParseResult;

import java.util.List;

public interface ChunkSplitter {

    /**
     * 将解析结果拆分为若干块。
     *
     * @param result 文档解析结果（含多页内容）
     * @param config      分块参数
     * @return 分块列表
     */
    List<ChunkResult> split(ParseResult result, ChunkConfig config);
}
