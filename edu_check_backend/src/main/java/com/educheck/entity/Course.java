package com.educheck.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 课程表实体类
 */
@Data
@TableName("course")
public class Course {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 课程名称
     */
    private String name;

    /**
     * 授课教师
     */
    private String teacher;

    /**
     * 上课地点
     */
    private String location;

    /**
     * 上课时间 HH:mm
     */
    private String startTime;

    /**
     * 下课时间 HH:mm
     */
    private String endTime;

    /**
     * 节次
     */
    private String section;

    /**
     * 星期几
     */
    private String weekDay;

    /**
     * 起始周
     */
    private Integer weekStart;

    /**
     * 结束周
     */
    private Integer weekEnd;

    /**
     * 教师用户ID
     */
    private Long teacherId;

    /**
     * 状态: 1启用 0停用
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