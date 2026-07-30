package com.educheck.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description="查寝打卡请求")
public class DormCheckinRequest {

    @Schema(description = "纬度")
    private BigDecimal locationLat;

    @Schema(description = "经度")
    private BigDecimal locationLng;

    @Schema(description = "定位位置")
    private String locationAddr;

    @Schema(description = "宿舍楼")
    private String building;

    @Schema(description = "宿舍号")
    private String room;

    @Schema(description = "是否在宿舍区域")
    private Boolean inDormArea;

    @Schema(description = "人脸图片Base64")
    private String faceImage;

    @Schema(description = "是否通过人脸验证")
    private Boolean faceVerified;



}
