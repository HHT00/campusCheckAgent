package com.educheck.controller;

import com.educheck.common.Result;
import com.educheck.common.TokenContextHolder;
import com.educheck.entity.DynamicCode;
import com.educheck.entity.Course;
import com.educheck.service.CourseService;
import com.educheck.service.DynamicCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/teacher/dynamic-code")
@RequiredArgsConstructor
@Tag(name = "动态码管理", description = "教师端动态码生成与管理")
public class DynamicCodeController {

    private final DynamicCodeService dynamicCodeService;
    private final CourseService courseService;
    private final TokenContextHolder tokenContextHolder;

    @PostMapping("/generate")
    @Operation(summary = "生成动态码")
    public Result<Map<String, Object>> generate(@RequestBody Map<String, Object> body) {
        Long teacherId = tokenContextHolder.requireCurrentUserId();
        Long courseId = body.get("courseId") != null ? ((Number) body.get("courseId")).longValue() : null;
        Integer duration = body.get("duration") != null ? ((Number) body.get("duration")).intValue() : 60;

        if (courseId == null) {
            return Result.error("课程ID不能为空");
        }

        Course course = courseService.getById(courseId);
        if (course == null) {
            return Result.error("课程不存在");
        }

        // 生成6位随机动态码
        String code = String.format("%06d", new Random().nextInt(999999));
        String sessionId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        DynamicCode dc = new DynamicCode();
        dc.setCourseId(courseId);
        dc.setTeacherId(teacherId);
        dc.setCode(code);
        dc.setSessionId(sessionId);
        dc.setDuration(duration);
        dc.setExpiredAt(LocalDateTime.now().plusSeconds(duration));
        dc.setUsed(0);
        dynamicCodeService.save(dc);

        Map<String, Object> data = new HashMap<>();
        data.put("dynamicCode", code);
        data.put("sessionId", sessionId);
        data.put("duration", duration);
        data.put("courseId", courseId);
        data.put("courseName", course.getName());

        log.info("教师 {} 为课程 {} 生成动态码 {}", teacherId, courseId, code);
        return Result.success(data);
    }

    @GetMapping("/active")
    @Operation(summary = "获取当前课程活跃的动态码")
    public Result<Map<String, Object>> getActive(@RequestParam Long courseId) {
        LocalDateTime now = LocalDateTime.now();

        DynamicCode active = dynamicCodeService.lambdaQuery()
                .eq(DynamicCode::getCourseId, courseId)
                .eq(DynamicCode::getUsed, 0)
                .gt(DynamicCode::getExpiredAt, now)
                .orderByDesc(DynamicCode::getCreatedAt)
                .last("LIMIT 1")
                .one();

        if (active == null) {
            return Result.success(Map.of("active", false));
        }

        Map<String, Object> data = new HashMap<>();
        data.put("active", true);
        data.put("dynamicCode", active.getCode());
        data.put("sessionId", active.getSessionId());
        data.put("duration", active.getDuration());
        data.put("expiredAt", active.getExpiredAt().toString());
        long remaining = java.time.Duration.between(now, active.getExpiredAt()).getSeconds();
        data.put("remaining", Math.max(0, remaining));
        return Result.success(data);
    }
}
