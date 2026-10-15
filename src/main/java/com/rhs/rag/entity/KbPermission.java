package com.rhs.rag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@TableName("kb_permission")
public class KbPermission {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("kb_id")
    private Long kbId;

    /** 授权主体类型：USER / DEPARTMENT */
    @TableField("subject_type")
    private String subjectType;

    /** 授权主体 ID：用户 ID 或部门 ID */
    @TableField("subject_id")
    private String subjectId;

    /** 权限级别：READ / WRITE / ADMIN */
    @TableField("permission")
    private String permission;

    @TableField("granted_by")
    private Long grantedBy;

    @TableField("granted_at")
    private LocalDateTime grantedAt;
}