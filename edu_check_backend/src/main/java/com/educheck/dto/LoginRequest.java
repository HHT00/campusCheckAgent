package com.educheck.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "登录请求")
public class LoginRequest {
    @NotBlank(message = "学号不能为空")
    @Schema(description = "学号")
    private String studentId;

    @NotBlank(message = "密码不能为空")
    @Schema(description = "密码")
    private String password;
}
