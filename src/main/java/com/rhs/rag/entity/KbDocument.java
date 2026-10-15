package com.rhs.rag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.rhs.rag.enums.DocumentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 上传到知识库的文档，一个文档对应多个分块
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("kb_document")
@Schema(description = "文档实体")
public class KbDocument {

    @Schema(description = "文档ID", example = "1")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "所属知识库ID", example = "1")
    @TableField("kb_id")
    private Long kbId;

    @Schema(description = "文件名", example = "技术方案.pdf")
    @TableField("file_name")
    private String fileName;

    @Schema(description = "文件类型", example = "PDF", allowableValues = {"PDF", "DOCX", "MD", "TXT"})
    @TableField("file_type")
    private String fileType;

    @Schema(description = "文件大小（字节）", example = "1024000")
    @TableField("file_size")
    private Long fileSize;

    @Schema(description = "MinIO中的对象路径", example = "1/2024/01/技术方案.pdf")
    @TableField("minio_path")
    private String minioPath;

    @Schema(description = "文档状态", example = "DONE")
    @TableField("status")
    private DocumentStatus status = DocumentStatus.PENDING;

    @Schema(description = "错误信息", example = "文件解析失败")
    @TableField("error_msg")
    private String errorMsg;

    @Schema(description = "分块数量", example = "15")
    @TableField("chunk_count")
    private Integer chunkCount;

    @Schema(description = "Token数量", example = "5000")
    @TableField("token_count")
    private Integer tokenCount;

    @Schema(description = "文档版本号", example = "1")
    @TableField("version")
    private Integer version;

    @Schema(description = "上传者用户ID", example = "1001")
    @TableField("uploaded_by")
    private Long uploadedBy;

    @Schema(description = "上传时间", example = "2024-01-01T10:00:00")
    @TableField("uploaded_at")
    private LocalDateTime uploadedAt;

    @Schema(description = "最近一次索引完成时间", example = "2024-01-01T10:30:00")
    @TableField("indexed_at")
    private LocalDateTime indexedAt;

    @Schema(description = "是否已删除", example = "false")
    @TableLogic(value = "false", delval = "true")
    @TableField("is_deleted")
    private Boolean isDeleted;
}
