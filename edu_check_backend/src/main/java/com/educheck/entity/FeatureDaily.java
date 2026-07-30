package com.educheck.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("feature_daily")
public class FeatureDaily {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private LocalDate date;

    /** 星期几 1-7 */
    private Integer weekDay;

    /** 当天应上课数 */
    private Integer classRequired;

    /** 签到数 */
    private Integer classPresent;

    /** 迟到数 */
    private Integer classLate;

    /** 旷课数 */
    private Integer classAbsent;

    /** 已查寝标记 */
    private Integer dormDone;

    /** 归寝时间 */
    private LocalTime dormTime;

    /** 宿舍楼 */
    private String dormBuilding;

    /** 是否晚归 */
    private Integer dormIsLate;

    /** 实习打卡 */
    private Integer internDone;

    /** 当天请假 */
    private Integer leaveToday;

    /** 近7天旷课数 */
    @TableField("absent_last_7d")
    private Integer absentLast7d;

    /** 前一天是否晚归 */
    private Integer prevDormLate;

    /** 今天是否有早课 */
    private Integer isMorningClass;

    /** 连续打卡天数 */
    private Integer streakDays;

    /** 近30天请假次数 */
    @TableField("leave_count_30d")
    private Integer leaveCount30d;

    /** 近30天周一病假次数 */
    @TableField("monday_sick_30d")
    private Integer mondaySick30d;

    /** 近7天迟到数 */
    @TableField("late_last_7d")
    private Integer lateLast7d;

    /** 近7天晚归数 */
    @TableField("dorm_late_last_7d")
    private Integer dormLateLast7d;

    /** 最后更新时间，数据库自动维护 */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
