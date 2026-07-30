package com.educheck.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("intern_checkin")
public class InternCheckin {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long internshipId;
    private LocalDate checkinDate;
    private LocalDateTime checkinTime;
    private Integer dayNumber;
    private String locationAddr;
    private String logContent;
    private Integer faceVerified;
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
