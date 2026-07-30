package com.educheck.controller;

import com.educheck.common.JwtUtil;
import com.educheck.common.Result;
import com.educheck.common.TokenContextHolder;
import com.educheck.dto.LoginRequest;
import com.educheck.entity.User;
import com.educheck.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "认证管理", description = "登录注册接口")
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    @Operation(summary = "用户登录")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        log.info("登录请求: studentId={}", request.getStudentId());

        // 按学号查用户 → 校验密码/状态 → 签发 JWT token → 返回用户信息

        User user = userService.lambdaQuery()
                .eq(User::getStudentId, request.getStudentId())
                .oneOpt()
                .orElse(null);

        // 用户不存在或密码错误均返回同一提示，防止枚举账号
        if (user == null) {
            log.warn("登录失败: 用户不存在 studentId={}", request.getStudentId());
            return Result.error(401, "学号或密码错误");
        }

        if (!user.getPassword().equals(request.getPassword())) {
            log.warn("登录失败: 密码不匹配");
            return Result.error(401, "学号或密码错误");
        }

        if (user.getStatus() != null && user.getStatus() == 0) {
            return Result.error(403, "账号已被禁用");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getRole());

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("userId", user.getId());
        data.put("name", user.getName());
        data.put("role", user.getRole());
        data.put("studentId", user.getStudentId());

        return Result.success("登录成功", data);
    }
}

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Tag(name = "用户管理", description = "用户信息接口")
class UserController {

    private final UserService userService;
    private final TokenContextHolder tokenContextHolder;

    @GetMapping("/profile")
    @Operation(summary = "获取当前用户信息")
    public Result<User> getProfile() {
        Long userId = tokenContextHolder.requireCurrentUserId();
        User user = userService.getById(userId);
        if (user == null) {
            return Result.error(404, "用户不存在");
        }
        // 返回用户信息，脱敏密码
        user.setPassword(null);
        return Result.success(user);
    }

    @PutMapping("/profile")
    @Operation(summary = "更新用户信息")
    public Result<User> updateProfile(@RequestBody User user) {
        Long userId = tokenContextHolder.requireCurrentUserId();
        user.setId(userId);
        // 不允许通过此接口修改密码，防止绕过
        user.setPassword(null);
        userService.updateById(user);
        User updated = userService.getById(userId);
        updated.setPassword(null);
        return Result.success(updated);
    }
}
