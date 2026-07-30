package com.educheck.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.educheck.common.Result;
import com.educheck.entity.AlertEvent;
import com.educheck.entity.FeatureDaily;
import com.educheck.service.AlertEventService;
import com.educheck.service.feature.FeatureDailyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
@Tag(name = "数据分析", description = "考勤数据分析与预警查询")
public class AnalysisController {

    private final FeatureDailyService featureDailyService;
    private final AlertEventService alertEventService;
    private final JdbcTemplate jdbc;

    /** 学生个人风险报告 */
    @GetMapping("/student/{userId}/risk")
    @Operation(summary = "学生考勤风险报告")
    public Result<Map<String, Object>> studentRisk(@PathVariable Long userId) {
        FeatureDaily today = featureDailyService.getByDate(userId, java.time.LocalDate.now());
        FeatureDaily yesterday = featureDailyService.getByDate(userId, java.time.LocalDate.now().minusDays(1));

        List<AlertEvent> alerts = alertEventService.lambdaQuery()
                .eq(AlertEvent::getUserId, userId)
                .eq(AlertEvent::getResolved, 0)
                .orderByDesc(AlertEvent::getCreatedAt)
                .last("LIMIT 5")
                .list();

        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> features = new LinkedHashMap<>();
        features.put("absentLast7d", today != null ? today.getAbsentLast7d() : 0);
        features.put("lateLast7d", today != null ? today.getLateLast7d() : 0);
        features.put("dormLateLast7d", today != null ? today.getDormLateLast7d() : 0);
        features.put("streakDays", today != null ? today.getStreakDays() : 0);
        features.put("leaveCount30d", today != null ? today.getLeaveCount30d() : 0);
        features.put("mondaySick30d", today != null ? today.getMondaySick30d() : 0);
        features.put("prevDormLate", yesterday != null ? yesterday.getDormIsLate() : 0);
        data.put("features", features);

        List<Map<String, Object>> alertList = alerts.stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("type", a.getAlertType());
            m.put("level", a.getRiskLevel());
            m.put("title", a.getTitle());
            m.put("detail", a.getDetail());
            m.put("createdAt", a.getCreatedAt());
            return m;
        }).collect(Collectors.toList());
        data.put("alerts", alertList);

        return Result.success(data);
    }

    /** 预警列表 */
    @GetMapping("/alerts")
    @Operation(summary = "预警列表")
    public Result<Page<AlertEvent>> alerts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "0") int resolved) {
        return Result.success(alertEventService.page(
                new Page<>(page, size),
                new LambdaQueryWrapper<AlertEvent>()
                        .eq(AlertEvent::getResolved, resolved)
                        .orderByDesc(AlertEvent::getCreatedAt)));
    }

    /** 宿舍晚归排行 */
    @GetMapping("/dorm/building-ranking")
    @Operation(summary = "宿舍晚归排行")
    public Result<List<Map<String, Object>>> dormRanking() {
        List<Map<String, Object>> list = jdbc.queryForList("""
            SELECT building, COUNT(*) as late_count
            FROM dorm_checkin
            WHERE status = 'late' AND date >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)
            GROUP BY building
            ORDER BY late_count DESC
            LIMIT 10
            """);
        return Result.success(list);
    }

    /** 课程出勤率排行 */
    @GetMapping("/course/ranking")
    @Operation(summary = "课程出勤排行")
    public Result<List<Map<String, Object>>> courseRanking() {
        List<Map<String, Object>> list = jdbc.queryForList("""
            SELECT c.name, COUNT(cc.id) as total,
                   SUM(CASE WHEN cc.status = 'present' THEN 1 ELSE 0 END) as present_count,
                   ROUND(SUM(CASE WHEN cc.status = 'present' THEN 1 ELSE 0 END) / COUNT(cc.id) * 100, 1) as rate
            FROM class_checkin cc
            JOIN course c ON c.id = cc.course_id
            WHERE cc.date >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
            GROUP BY cc.course_id
            ORDER BY rate ASC
            LIMIT 10
            """);
        return Result.success(list);
    }

    /** 请假模式异常排行 */
    @GetMapping("/anomaly/leave-pattern")
    @Operation(summary = "请假异常排行")
    public Result<List<Map<String, Object>>> leaveAnomaly() {
        List<Map<String, Object>> list = jdbc.queryForList("""
            SELECT l.user_id, u.name,
                   COUNT(*) as total_leaves,
                   SUM(CASE WHEN l.type = 'sick' AND DAYOFWEEK(l.start_date) = 2 THEN 1 ELSE 0 END) as monday_sick,
                   ROUND(SUM(CASE WHEN l.type = 'sick' AND DAYOFWEEK(l.start_date) = 2 THEN 1 ELSE 0 END) / COUNT(*) * 100, 1) as monday_ratio
            FROM leave_record l
            JOIN user u ON u.id = l.user_id
            WHERE l.created_at >= DATE_SUB(NOW(), INTERVAL 90 DAY)
            GROUP BY l.user_id
            HAVING monday_sick >= 2
            ORDER BY monday_sick DESC
            """);
        return Result.success(list);
    }
}
