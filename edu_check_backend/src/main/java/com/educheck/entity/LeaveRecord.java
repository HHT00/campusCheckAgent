package com.educheck.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 请假记录实体类
 */
@Data
@TableName("leave_record")
public class LeaveRecord {

    /** 主键ID - 自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID（请假人） */
    private Long userId;

    /** 请假类型：sick-病假 personal-事假 official-公假 annual-年假 */
    private String type;

    /** 开始日期 */
    private LocalDate startDate;

    /** 结束日期 */
    private LocalDate endDate;

    /** 请假天数 */
    private Integer days;

    /** 请假事由 */
    private String reason;

    /** 状态：pending-待审批 approved-已批准 rejected-已驳回 */
    private String status;

    /** 驳回原因 */
    private String rejectReason;

    /** 审批人ID */
    private Long approveUserId;

    /** 审批时间 */
    private LocalDateTime approveTime;

    /** 创建时间 - 自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 - 自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
