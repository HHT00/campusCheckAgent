package com.educheck.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.educheck.common.Result;
import com.educheck.common.TokenContextHolder;
import com.educheck.entity.Notification;
import com.educheck.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "通知管理", description = "通知消息接口")
public class NotificationController {

    private final NotificationService notificationService;
    private final TokenContextHolder tokenContextHolder;

    @GetMapping
    @Operation(summary = "获取通知列表")
    public Result<Page<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = tokenContextHolder.requireCurrentUserId();

        Page<Notification> p = notificationService.page(
                new Page<>(page, size),
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .orderByDesc(Notification::getCreatedAt));

        Page<Map<String, Object>> result = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        result.setRecords(p.getRecords().stream().map(n -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.getId());
            m.put("title", n.getTitle());
            m.put("content", n.getContent());
            m.put("type", n.getType());
            m.put("isRead", n.getIsRead());
            m.put("createdAt", n.getCreatedAt());
            return m;
        }).toList());
        return Result.success(result);
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "标记已读")
    public Result<Void> markRead(@PathVariable Long id) {
        Notification n = notificationService.getById(id);
        if (n != null) {
            n.setIsRead(1);
            notificationService.updateById(n);
        }
        return Result.success(null);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "获取未读通知数")
    public Result<Map<String, Object>> unreadCount() {
        Long userId = tokenContextHolder.requireCurrentUserId();
        long count = notificationService.lambdaQuery()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0)
                .count();
        return Result.success(Map.of("count", (int) count));
    }
}
