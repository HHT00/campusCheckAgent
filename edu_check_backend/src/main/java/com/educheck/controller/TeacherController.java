package com.educheck.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.educheck.common.Result;
import com.educheck.common.TokenContextHolder;
import com.educheck.entity.*;
import com.educheck.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/teacher")
@RequiredArgsConstructor
@Tag(name = "教师端管理", description = "教师端学生管理、打卡概览等")
public class TeacherController {

    private final UserService userService;
    private final ClassCheckinService classCheckinService;
    private final DormCheckinService dormCheckinService;
    private final TokenContextHolder tokenContextHolder;

    // ==================== 学生管理 ====================

    @GetMapping("/students/page")
    @Operation(summary = "分页获取学生列表（含今日签到/查寝状态）")
    public Result<Page<Map<String, Object>>> getStudentsPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String keyword) {

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(User::getRole, "student")
                .orderByAsc(User::getStudentId);

        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(User::getName, keyword)
                    .or().like(User::getStudentId, keyword));
        }

        Page<User> userPage = userService.page(new Page<>(page, size), wrapper);
        LocalDate today = LocalDate.now();

        List<Map<String, Object>> records = userPage.getRecords().stream().map(user -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", user.getId());
            item.put("name", user.getName());
            item.put("studentId", user.getStudentId());
            item.put("college", user.getCollege());
            item.put("major", user.getMajor());
            item.put("grade", user.getGrade());
            item.put("avatar", user.getAvatar());

            // 今日上课签到状态
            List<ClassCheckin> classList = classCheckinService.lambdaQuery()
                    .eq(ClassCheckin::getUserId, user.getId())
                    .eq(ClassCheckin::getDate, today)
                    .list();
            boolean hasPresent = classList.stream().anyMatch(c -> "present".equals(c.getStatus()));
            boolean hasLate = classList.stream().anyMatch(c -> "late".equals(c.getStatus()));
            boolean hasAbsent = classList.stream().anyMatch(c -> "absent".equals(c.getStatus()));
            if (hasPresent) item.put("todayCheckin", "present");
            else if (hasLate) item.put("todayCheckin", "late");
            else if (hasAbsent) item.put("todayCheckin", "absent");
            else item.put("todayCheckin", "none");

            // 今日查寝状态
            DormCheckin dorm = dormCheckinService.lambdaQuery()
                    .eq(DormCheckin::getUserId, user.getId())
                    .eq(DormCheckin::getDate, today)
                    .one();
            if (dorm != null) {
                item.put("todayDorm", "late".equals(dorm.getStatus()) ? "late" : "normal");
            } else {
                item.put("todayDorm", "none");
            }

            // 累计统计
            long classTotal = classCheckinService.lambdaQuery()
                    .eq(ClassCheckin::getUserId, user.getId())
                    .count();
            long dormTotal = dormCheckinService.lambdaQuery()
                    .eq(DormCheckin::getUserId, user.getId())
                    .count();
            item.put("classTotal", (int) classTotal);
            item.put("dormTotal", (int) dormTotal);

            return item;
        }).collect(Collectors.toList());

        Page<Map<String, Object>> result = new Page<>(userPage.getCurrent(), userPage.getSize(), userPage.getTotal());
        result.setRecords(records);
        return Result.success(result);
    }

    @GetMapping("/students/checkin/{studentId}")
    @Operation(summary = "获取学生打卡详情")
    public Result<Map<String, Object>> getStudentCheckinDetail(@PathVariable Long studentId) {
        User student = userService.getById(studentId);
        if (student == null) {
            return Result.error("学生不存在");
        }

        LocalDate today = LocalDate.now();
        Map<String, Object> result = new LinkedHashMap<>();

        // 学生基本信息
        Map<String, Object> stuInfo = new LinkedHashMap<>();
        stuInfo.put("id", student.getId());
        stuInfo.put("name", student.getName());
        stuInfo.put("studentId", student.getStudentId());
        stuInfo.put("college", student.getCollege());
        stuInfo.put("major", student.getMajor());
        stuInfo.put("grade", student.getGrade());
        result.put("student", stuInfo);

        // 统计
        long classTotal = classCheckinService.lambdaQuery()
                .eq(ClassCheckin::getUserId, studentId).count();
        long dormTotal = dormCheckinService.lambdaQuery()
                .eq(DormCheckin::getUserId, studentId).count();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("classTotal", (int) classTotal);
        stats.put("dormTotal", (int) dormTotal);
        stats.put("streakDays", 0);
        stats.put("totalPoints", 0);
        result.put("stats", stats);

        // 今日课程签到
        List<ClassCheckin> todayClasses = classCheckinService.lambdaQuery()
                .eq(ClassCheckin::getUserId, studentId)
                .eq(ClassCheckin::getDate, today)
                .list();
        List<Map<String, Object>> classList = todayClasses.stream().map(c -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", c.getId());
            item.put("courseId", c.getCourseId());
            item.put("status", c.getStatus());
            item.put("checkinTime", c.getCheckinTime() != null ? c.getCheckinTime().toString() : null);
            return item;
        }).collect(Collectors.toList());
        result.put("todayClasses", classList);

        // 今日查寝
        DormCheckin dorm = dormCheckinService.lambdaQuery()
                .eq(DormCheckin::getUserId, studentId)
                .eq(DormCheckin::getDate, today)
                .one();
        if (dorm != null) {
            Map<String, Object> dormMap = new LinkedHashMap<>();
            dormMap.put("id", dorm.getId());
            dormMap.put("status", dorm.getStatus());
            dormMap.put("checkinTime", dorm.getCheckinTime() != null ? dorm.getCheckinTime().toString() : null);
            dormMap.put("locationAddr", dorm.getLocationAddr());
            dormMap.put("building", dorm.getBuilding());
            result.put("todayDorm", dormMap);
        } else {
            result.put("todayDorm", null);
        }

        // 近期上课记录（近7天）
        List<ClassCheckin> recentClass = classCheckinService.lambdaQuery()
                .eq(ClassCheckin::getUserId, studentId)
                .ge(ClassCheckin::getDate, today.minusDays(7))
                .orderByDesc(ClassCheckin::getDate)
                .last("LIMIT 10")
                .list();
        result.put("recentClassRecords", recentClass.stream().map(c -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", c.getId());
            item.put("date", c.getDate() != null ? c.getDate().toString() : null);
            item.put("checkinTime", c.getCheckinTime() != null ? c.getCheckinTime().toString() : null);
            item.put("status", c.getStatus());
            return item;
        }).collect(Collectors.toList()));

        // 近期查寝记录
        List<DormCheckin> recentDorm = dormCheckinService.lambdaQuery()
                .eq(DormCheckin::getUserId, studentId)
                .ge(DormCheckin::getDate, today.minusDays(7))
                .orderByDesc(DormCheckin::getDate)
                .last("LIMIT 10")
                .list();
        result.put("recentDormRecords", recentDorm.stream().map(d -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", d.getId());
            item.put("date", d.getDate() != null ? d.getDate().toString() : null);
            item.put("checkinTime", d.getCheckinTime() != null ? d.getCheckinTime().toString() : null);
            item.put("status", d.getStatus());
            return item;
        }).collect(Collectors.toList()));

        return Result.success(result);
    }

    // ==================== 今日签到总结 ====================

    @GetMapping("/checkin/today-summary")
    @Operation(summary = "获取今日签到总结")
    public Result<Map<String, Object>> getTodaySummary() {
        LocalDate today = LocalDate.now();
        List<User> students = userService.lambdaQuery()
                .eq(User::getRole, "student")
                .eq(User::getStatus, 1)
                .list();

        int total = students.size();
        int classChecked = 0;
        int dormChecked = 0;

        for (User student : students) {
            boolean hasClassCheckin = classCheckinService.lambdaQuery()
                    .eq(ClassCheckin::getUserId, student.getId())
                    .eq(ClassCheckin::getDate, today)
                    .in(ClassCheckin::getStatus, "present", "late")
                    .count() > 0;
            if (hasClassCheckin) classChecked++;

            boolean hasDormCheckin = dormCheckinService.lambdaQuery()
                    .eq(DormCheckin::getUserId, student.getId())
                    .eq(DormCheckin::getDate, today)
                    .count() > 0;
            if (hasDormCheckin) dormChecked++;
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalStudents", total);
        summary.put("classChecked", classChecked);
        summary.put("classNotChecked", total - classChecked);
        summary.put("dormChecked", dormChecked);
        summary.put("dormNotChecked", total - dormChecked);

        return Result.success(summary);
    }

    // ==================== 预警管理 ====================

    @GetMapping("/alerts")
    @Operation(summary = "获取预警列表")
    public Result<Page<AlertEvent>> getAlerts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(alertEventService.page(
                new Page<>(page, size),
                new LambdaQueryWrapper<AlertEvent>()
                        .eq(AlertEvent::getResolved, 0)
                        .orderByDesc(AlertEvent::getCreatedAt)));
    }

    @PostMapping("/alerts/{id}/resolve")
    @Operation(summary = "标记预警已处理")
    public Result<String> resolveAlert(@PathVariable Long id) {
        AlertEvent alert = alertEventService.getById(id);
        if (alert == null) return Result.error("预警不存在");
        alert.setResolved(1);
        alert.setResolvedAt(java.time.LocalDateTime.now());
        alertEventService.updateById(alert);
        return Result.success("已处理");
    }

    private final AlertEventService alertEventService;
}
