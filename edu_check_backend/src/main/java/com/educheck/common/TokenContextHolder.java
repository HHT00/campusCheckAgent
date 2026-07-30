package com.educheck.common;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * JWT Token 上下文工具
 * 从当前请求的 Authorization 头中解析出用户 ID
 * 所有 Controller 通过此工具获取当前登录用户，禁止硬编码 userId
 */
@Component
@RequiredArgsConstructor
public class TokenContextHolder {

    private final JwtUtil jwtUtil;

    /**
     * 获取当前登录用户的 ID
     * @return 用户ID，未登录或 token 无效时返回 null
     */
    public Long getCurrentUserId() {
        String token = getTokenFromRequest();
        if (token == null || token.isEmpty()) {
            return null;
        }
        try {
            return jwtUtil.getUserIdFromToken(token);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取当前登录用户的角色
     * @return 角色字符串，未登录或 token 无效时返回 null
     */
    public String getCurrentRole() {
        String token = getTokenFromRequest();
        if (token == null || token.isEmpty()) {
            return null;
        }
        try {
            return jwtUtil.getRoleFromToken(token);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从当前请求中提取 Authorization 头
     */
    private String getTokenFromRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        String authHeader = request.getHeader("Authorization");
        // 兼容带 "Bearer " 前缀的 token
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return authHeader;
    }

    /**
     * 获取当前用户 ID，并校验是否已登录
     * @throws ServiceException 401 未登录
     */
    public Long requireCurrentUserId() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            throw new ServiceException(401, "未登录或 token 已失效");
        }
        return userId;
    }
}
