package com.rhs.rag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.rhs.rag.enums.TaskStatus;
import com.rhs.rag.enums.TaskType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.ibatis.type.JdbcType;

import java.time.LocalDateTime;

/**
 * 索引任务，用于异步索引状态管理
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("kb_index_task")
public class IndexTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("doc_id")
    private Long docId;

    @TableField(value = "task_type", jdbcType = JdbcType.VARCHAR)
    private String taskType = TaskType.INDEX.toString();

    /**
     * 任务自身状态，与 {@code kb_document.status} 不是同一套枚举。
     * 文档侧使用 PROCESSING，任务侧使用 RUNNING。
     */
    @TableField(value = "status", jdbcType = JdbcType.VARCHAR)
    private TaskStatus status = TaskStatus.PENDING;

    @TableField("retry_count")
    private Integer retryCount = 0;

    @TableField("max_retry")
    private Integer maxRetry = 3;

    @TableField("error_msg")
    private String errorMsg;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("finished_at")
    private LocalDateTime finishedAt;


    public boolean canRetry() {
        return status == TaskStatus.FAILED
                && retryCount != null
                && maxRetry != null
                && retryCount < maxRetry;
    }
}
