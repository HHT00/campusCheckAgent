package com.educheck.dto;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

//自动生成get set 方法
@Data
@Schema(description = "人脸录入请求")
public class FaceRegisterRequest {


// 用来生成文档
@Schema(description = "人脸图片base64数据")
private String image;

//图片格式
@Schema(description = "图片格式")
    private  String format ="jpg";

}
