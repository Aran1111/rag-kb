package com.rhs.rag.service.loader;

import com.rhs.rag.result.ParseResult;

import java.io.InputStream;

public interface DocumentParser {

    /**
     * 支持文件的类型
     */
    String supportType();

    /**
     * 解析文件，返回解析结果
     */
    ParseResult parse(InputStream input, String filename);
}
