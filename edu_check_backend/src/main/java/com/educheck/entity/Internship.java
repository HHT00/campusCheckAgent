package com.educheck.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("internship")
public class Internship {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String company;
    private String role;
    private Integer totalDays;
    private Integer completedDays;
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
