package com.educheck.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("intern_photo")
public class InternPhoto {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long checkinId;
    private String photoUrl;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
