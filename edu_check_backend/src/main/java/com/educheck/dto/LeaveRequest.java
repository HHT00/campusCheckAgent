package com.educheck.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 请假申请请求 DTO
 */
@Data
@Schema(description = "请假申请请求")
public class LeaveRequest {

    @Schema(description = "请假类型：sick-病假 personal-事假 official-公假 annual-年假")
    private String type;

    @Schema(description = "开始日期（yyyy-MM-dd）")
    private String startDate;

    @Schema(description = "结束日期（yyyy-MM-dd）")
    private String endDate;

    @Schema(description = "请假事由")
    private String reason;
}
