package com.educheck.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 请假审批请求 DTO
 */
@Data
@Schema(description = "请假审批请求")
public class LeaveApproveRequest {

    @Schema(description = "是否批准：true-批准 false-驳回")
    private Boolean approved;

    @Schema(description = "驳回原因（驳回时必填）")
    private String rejectReason;
}
