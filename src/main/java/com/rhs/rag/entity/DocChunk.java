package com.rhs.rag.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.rhs.rag.handler.PgVectorTypeHandler;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 文档分块，每条记录是一个可检索的最小单元
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("kb_doc_chunk")
public class DocChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("doc_id")
    private Long docId;

    /** 冗余知识库 ID，检索时避免 JOIN */
    @TableField("kb_id")
    private Long kbId;

    /** 在文档中的顺序（0-based） */
    @TableField("chunk_index")
    private Integer chunkIndex;

    /** 分块原文 */
    @TableField("content")
    private String content;

    /**
     * 全文检索向量，由数据库触发器根据 content 自动维护，Java 侧不写入。
     */
    @TableField(value = "content_tsv", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private String contentTsv;

    /**
     * 向量（text-embedding-v3，1024 维）。
     * 以 pgvector 文本格式存储，例如：[0.12, -0.03, ...]
     */
    @TableField(value = "embedding", typeHandler = PgVectorTypeHandler.class)
    private float[] embedding;

    /** 来自文档第几页（PDF 专用） */
    @TableField("page_num")
    private Integer pageNum;

    /** 所在章节标题（如果能识别） */
    @TableField("section_title")
    private String sectionTitle;

    /** 该块的 Token 估算数 */
    @TableField("token_count")
    private Integer tokenCount;

    /** 对应的文档版本号，重建索引后删除旧版本时使用 */
    @TableField("doc_version")
    private Integer docVersion;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
