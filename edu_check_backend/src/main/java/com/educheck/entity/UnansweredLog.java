package com.educheck.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("unanswered_log")
public class UnansweredLog {

//    主键id
    @TableId(type = IdType.AUTO)
    private Long id ;

//    用户分类问题
    private String question ;

//    分类
    private String category;

//    知识库是否补充该问题
    private Integer answered;

    @TableField(value = "created_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime createAt;



}
