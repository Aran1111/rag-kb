package com.jichi.ragkb.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("kb_chat_session")
public class ChatSession {

    /**
     * 主键为 String 类型，原 JPA 未指定生成策略，这里使用 INPUT 表示手动赋值。
     * 若希望自动生成 UUID，可改为 IdType.ASSIGN_UUID。
     */
    @TableId(type = IdType.AUTO)
    private String id;

    @TableField("user_id")
    private Long userId;

    @TableField("kb_ids")
    private String kbIds;

    @TableField("title")
    private String title;

    @TableField("message_count")
    private Integer messageCount = 0;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField("last_active_at")
    private LocalDateTime lastActiveAt = LocalDateTime.now();

    @TableField("is_deleted")
    private Boolean isDeleted = false;
}