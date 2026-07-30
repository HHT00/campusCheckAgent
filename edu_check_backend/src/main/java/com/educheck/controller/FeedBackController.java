package com.educheck.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.educheck.common.Result;
import com.educheck.common.TokenContextHolder;
import com.educheck.entity.FeedBack;
import com.educheck.service.FeedBackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

//日志注解
@Slf4j
//接口返回的数据格式为json
@RestController
//定义当前控制的处理的接口
@RequestMapping("/api/feedback")

//创建service对象
//给所有的final修饰的成员变量 创建对应的对象
@RequiredArgsConstructor

//
@Tag(name = "意见反馈", description = "意见反馈接口")
public class FeedBackController {

    private final FeedBackService feedbackService;
    private final TokenContextHolder tokenContextHolder;

    private static final Map<String, String> TYPE_NAME_MAP = Map.of(
            "suggestion", "建议",
            "bug", "问题反馈",
            "complaint", "投诉",
            "other", "其他"
    );

    @PostMapping("/submit")
    @Operation(summary = "提交反馈")
    public Result<Void> submit(@RequestBody Map<String, String> body) {
        String type = body.get("type");
        String content = body.get("content");
        String contact = body.get("contact");

        if (type == null || !TYPE_NAME_MAP.containsKey(type)) {
            return Result.error("无效的反馈类型");
        }
        if (content == null || content.trim().isEmpty()) {
            return Result.error("反馈内容不能为空");
        }

        FeedBack feedback = new FeedBack();
        feedback.setUserId(tokenContextHolder.requireCurrentUserId());
        feedback.setType(type);
        feedback.setTypeName(TYPE_NAME_MAP.get(type));
        feedback.setContent(content.trim());
        feedback.setContact(contact != null ? contact.trim() : null);
        feedback.setStatus(0);
        feedback.setCreatedAt(LocalDateTime.now());
        feedbackService.save(feedback);
        return Result.success("提交成功", null);
    }

    @GetMapping("/list")
    @Operation(summary = "获取我的反馈列表")
    public Result<Page<Map<String, Object>>> myList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = tokenContextHolder.requireCurrentUserId();

        Page<FeedBack> p = feedbackService.page(
                new Page<>(page, size),
                new LambdaQueryWrapper<FeedBack>()
                        .eq(FeedBack::getUserId, userId)
                        .orderByDesc(FeedBack::getCreatedAt));

        return Result.success(toPage(p, false));
    }

    @GetMapping("/teacher")
    @Operation(summary = "教师获取全部反馈列表")
    public Result<Page<Map<String, Object>>> teacherList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<FeedBack> p = feedbackService.page(
                new Page<>(page, size),
                new LambdaQueryWrapper<FeedBack>()
                        .orderByDesc(FeedBack::getCreatedAt));

        return Result.success(toPage(p, true));
    }

    /** 将 FeedBack 分页转为前端需要的格式 */
    private Page<Map<String, Object>> toPage(Page<FeedBack> p, boolean withUser) {
        Page<Map<String, Object>> result = new Page<>(p.getCurrent(), p.getSize());
        result.setTotal(p.getTotal());

        if (p.getRecords().isEmpty()) {
            result.setRecords(List.of());
            return result;
        }

        // 如果需要用户信息，批量查询 user 表
        Map<Long, com.educheck.entity.User> userMap = new HashMap<>();
        if (withUser) {
            List<Long> userIds = p.getRecords().stream()
                    .map(FeedBack::getUserId)
                    .distinct()
                    .collect(Collectors.toList());
            // 这里注入 UserService 来查，或者直接 lambdaQuery
            // 简单起见略过，实际注入 private UserService userService;
        }

        List<Map<String, Object>> records = p.getRecords().stream().map(f -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", f.getId());
            item.put("type", f.getType());
            item.put("typeName", f.getTypeName());
            item.put("content", f.getContent());
            item.put("contact", f.getContact());
            item.put("createdAt", f.getCreatedAt());

            if (withUser) {
                // 从 userMap 取用户信息
                // item.put("studentName", ...);
                // item.put("studentId", ...);
            }
            return item;
        }).collect(Collectors.toList());

        result.setRecords(records);
        return result;
    }
}
