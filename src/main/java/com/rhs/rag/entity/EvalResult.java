package com.rhs.rag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("kb_eval_result")
public class EvalResult {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("dataset_id")
    private Long datasetId;

    @TableField("eval_version")
    private String evalVersion;

    @TableField("hit")
    private Boolean hit;

    @TableField("rank")
    private Integer rank;

    @TableField("actual_answer")
    private String actualAnswer;

    @TableField("faithfulness")
    private Double faithfulness;

    @TableField("answer_relevancy")
    private Double answerRelevancy;

    @TableField("eval_at")
    private LocalDateTime evalAt = LocalDateTime.now();
}