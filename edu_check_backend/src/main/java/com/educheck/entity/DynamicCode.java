package com.educheck.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dynamic_code")
public class DynamicCode {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long courseId;

    private Long teacherId;

    private String code;

    private String sessionId;

    private Integer duration;

    private LocalDateTime expiredAt;

    private Integer used;

    private LocalDateTime createdAt;
}
