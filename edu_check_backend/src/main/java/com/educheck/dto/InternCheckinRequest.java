package com.educheck.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "实习打卡请求")
public class InternCheckinRequest {
    @Schema(description = "实习ID")
    private Long internshipId;
    @Schema(description = "定位地址")
    private String locationAddr;
    @Schema(description = "日志内容")
    private String logContent;
    @Schema(description = "是否通过人脸验证")
    private Boolean faceVerified;
}
