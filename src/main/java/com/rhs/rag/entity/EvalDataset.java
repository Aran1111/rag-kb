package com.rhs.rag.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.rhs.rag.handler.LongArrayTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("kb_eval_dataset")
public class EvalDataset {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("kb_id")
    private Long kbId;

    @TableField("question")
    private String question;

    @TableField("expected_answer")
    private String expectedAnswer;

    /**
     * PostgreSQL BIGINT[] 数组。
     * 需要自定义 TypeHandler 才能正确读写，否则会报类型转换错误。
     */
    @TableField(value = "expected_chunk_ids", typeHandler = LongArrayTypeHandler.class)
    private Long[] expectedChunkIds;

    @TableField("created_by")
    private Long createdBy;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}