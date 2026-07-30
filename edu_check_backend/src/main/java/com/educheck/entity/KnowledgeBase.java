package com.educheck.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

//自动生成get set 方法
@Data

@TableName("knowledge_base")
public class KnowledgeBase {

//    主键id
    @TableId(type = IdType.AUTO)
    private Long id ;

//    问题分类

    private  String category;

//    问题
    private String question;

//    对应的回答问题答案
    private String answer;

//    关键字
    private String keywords;

//    同义词扩展
    private String synonyms ;

//    排序
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT_UPDATE)
//    创建时间
    private LocalDateTime createdAt;
}
