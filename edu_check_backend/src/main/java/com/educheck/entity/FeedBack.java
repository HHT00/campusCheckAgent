package com.educheck.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 意见反馈表实体类
 */
@Data
@TableName("feedback")
public class FeedBack {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 提交用户ID
     */
    private Long userId;

    /**
     * 类型: suggestion/bug/complaint/other
     */
    private String type;

    /**
     * 类型中文名: 建议/问题反馈/投诉/其他
     */
    private String typeName;

    /**
     * 反馈内容
     */
    private String content;

    /**
     * 联系方式
     */
    private String contact;

    /**
     * 状态: 0未处理 1已处理
     */
    private Integer status;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
