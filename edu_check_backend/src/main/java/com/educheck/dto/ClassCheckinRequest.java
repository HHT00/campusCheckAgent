package com.educheck.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "上课签到请求")
public class ClassCheckinRequest {

    @Schema(description = "课程id")
    private Long courseId;

    @Schema(description = "签到方式")
    private String method;

    @Schema(description = "纬度")
    private BigDecimal locationLat;

    @Schema(description = "经度")
    private BigDecimal locationLng;

    @Schema(description = "定位地址")
    private String locationAddr;

    @Schema(description = "动态码")
    private String dynamicCode;

    @Schema(description = "签到会话ID")
    private String sessionId;

    @Schema(description = "是否通过人脸验证")
    private Boolean faceVerified;
}
