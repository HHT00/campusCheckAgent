package com.educheck.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

//自动 get set
@Data


@Schema(description = "人脸认证请求")
public class FaceVerifyRequest {
    @Schema(description = "当前图片的base64数据")
    private String image;
    @Schema(description = "图片格式")
    private String format= "jpg";
}
