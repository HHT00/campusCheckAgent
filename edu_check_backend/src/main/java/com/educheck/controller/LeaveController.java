package com.educheck.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.educheck.common.Result;
import com.educheck.common.TokenContextHolder;
import com.educheck.dto.LeaveApproveRequest;
import com.educheck.dto.LeaveRequest;
import com.educheck.entity.LeaveRecord;
import com.educheck.entity.User;
import com.educheck.service.LeaveRecordService;
import com.educheck.service.UserService;
import com.educheck.service.feature.FeatureDailyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/leaves")
@RequiredArgsConstructor
@Tag(name = "请假管理", description = "请假申请与审批接口")
public class LeaveController {

    private final LeaveRecordService leaveRecordService;
    private final TokenContextHolder tokenContextHolder;
    private final UserService userService;
    private final FeatureDailyService featureDailyService;

    /**
     * 请假类型 与 中文名称 / CSS 类名的映射
     */
    private static final Map<String, String> TYPE_NAME_MAP = new LinkedHashMap<>();
    private static final Map<String, String> TYPE_CLASS_MAP = new LinkedHashMap<>();
    private static final Map<String, String> STATUS_NAME_MAP = new LinkedHashMap<>();
    private static final Map<String, String> STATUS_CLASS_MAP = new LinkedHashMap<>();

    static {
        TYPE_NAME_MAP.put("sick", "病假");
        TYPE_NAME_MAP.put("personal", "事假");
        TYPE_NAME_MAP.put("official", "公假");
        TYPE_NAME_MAP.put("annual", "年假");

        TYPE_CLASS_MAP.put("sick", "sick");
        TYPE_CLASS_MAP.put("personal", "personal");
        TYPE_CLASS_MAP.put("official", "official");
        TYPE_CLASS_MAP.put("annual", "annual");

        STATUS_NAME_MAP.put("pending", "待审批");
        STATUS_NAME_MAP.put("approved", "已通过");
        STATUS_NAME_MAP.put("rejected", "已驳回");

        STATUS_CLASS_MAP.put("pending", "status-pending");
        STATUS_CLASS_MAP.put("approved", "status-approved");
        STATUS_CLASS_MAP.put("rejected", "status-rejected");
    }

    // ==================== 学生端 ====================

    /**
     * 提交请假申请
     */
    @PostMapping
    @Operation(summary = "提交请假申请")
    public Result<LeaveRecord> applyLeave(@RequestBody LeaveRequest request) {
        Long userId = tokenContextHolder.requireCurrentUserId();

        // 校验参数
        if (request.getType() == null || request.getType().isEmpty()) {
            return Result.error("请假类型不能为空");
        }
        if (request.getStartDate() == null || request.getEndDate() == null) {
            return Result.error("请假起止日期不能为空");
        }
        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            return Result.error("请假事由不能为空");
        }

        // 转换日期
        LocalDate startDate;
        LocalDate endDate;
        try {
            startDate = LocalDate.parse(request.getStartDate());
            endDate = LocalDate.parse(request.getEndDate());
        } catch (Exception e) {
            return Result.error("日期格式错误，应为 yyyy-MM-dd");
        }

        // 校验日期
        if (endDate.isBefore(startDate)) {
            return Result.error("结束日期不能早于开始日期");
        }

        // 计算请假天数
        int days = (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;

        // 创建请假记录
        LeaveRecord leave = new LeaveRecord();
        leave.setUserId(userId);
        leave.setType(request.getType());
        leave.setStartDate(startDate);
        leave.setEndDate(endDate);
        leave.setDays(days);
        leave.setReason(request.getReason().trim());
        leave.setStatus("pending"); // 初始状态为待审批

        // 保存到数据库
        leaveRecordService.save(leave);

        log.info("用户 {} 提交请假申请，id={}", userId, leave.getId());
        return Result.success("提交成功", leave);
    }

    /**
     * 获取当前用户的请假列表（分页 + 按状态筛选）
     */
    @GetMapping
    @Operation(summary = "获取请假列表")
    public Result<Page<Map<String, Object>>> getLeaves(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String type) {
        Long userId = tokenContextHolder.requireCurrentUserId();

        // 构建查询条件
        LambdaQueryWrapper<LeaveRecord> wrapper = new LambdaQueryWrapper<LeaveRecord>()
                .eq(LeaveRecord::getUserId, userId)
                .orderByDesc(LeaveRecord::getCreatedAt);

        // 按状态筛选（前端 type 参数实际传递的是状态值）
        if (type != null && !type.isEmpty()) {
            wrapper.eq(LeaveRecord::getStatus, type);
        }

        // 分页查询
        Page<LeaveRecord> leavePage = leaveRecordService.page(new Page<>(page, size), wrapper);

        // 组装前端需要的展示字段
        List<Map<String, Object>> records = leavePage.getRecords().stream().map(leave -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", leave.getId());
            item.put("type", leave.getType());
            item.put("typeName", TYPE_NAME_MAP.getOrDefault(leave.getType(), leave.getType()));
            item.put("typeClass", TYPE_CLASS_MAP.getOrDefault(leave.getType(), ""));
            item.put("status", leave.getStatus());
            item.put("statusName", STATUS_NAME_MAP.getOrDefault(leave.getStatus(), leave.getStatus()));
            item.put("statusClass", STATUS_CLASS_MAP.getOrDefault(leave.getStatus(), ""));
            item.put("startDate", leave.getStartDate() != null ? leave.getStartDate().toString() : "");
            item.put("endDate", leave.getEndDate() != null ? leave.getEndDate().toString() : "");
            item.put("days", leave.getDays());
            item.put("reason", leave.getReason());
            item.put("rejectReason", leave.getRejectReason());
            item.put("submitTime", leave.getCreatedAt() != null
                    ? leave.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "");
            item.put("approveTime", leave.getApproveTime() != null
                    ? leave.getApproveTime().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : null);
            return item;
        }).collect(Collectors.toList());

        // 构造分页结果
        Page<Map<String, Object>> pageResult = new Page<>(leavePage.getCurrent(), leavePage.getSize(), leavePage.getTotal());
        pageResult.setRecords(records);

        return Result.success(pageResult);
    }

    /**
     * 获取当前用户的请假统计
     */
    @GetMapping("/stats")
    @Operation(summary = "获取请假统计")
    public Result<Map<String, Object>> getLeaveStats() {
        Long userId = tokenContextHolder.requireCurrentUserId();

        // 查询当前用户的所有请假记录，按状态分组统计
        List<LeaveRecord> allLeaves = leaveRecordService.lambdaQuery()
                .eq(LeaveRecord::getUserId, userId)
                .list();

        int total = allLeaves.size();
        int pending = 0;
        int approved = 0;
        int rejected = 0;

        for (LeaveRecord leave : allLeaves) {
            switch (leave.getStatus()) {
                case "pending" -> pending++;
                case "approved" -> approved++;
                case "rejected" -> rejected++;
            }
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("pending", pending);
        stats.put("approved", approved);
        stats.put("rejected", rejected);

        return Result.success(stats);
    }

    /**
     * 获取请假详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取请假详情")
    public Result<Map<String, Object>> getLeaveDetail(@PathVariable Long id) {
        Long userId = tokenContextHolder.requireCurrentUserId();

        LeaveRecord leave = leaveRecordService.getById(id);
        if (leave == null) {
            return Result.error("请假记录不存在");
        }

        // 校验只能查看自己的请假记录（教师可以查看所有）
        String role = tokenContextHolder.getCurrentRole();
        if (!"teacher".equals(role) && !leave.getUserId().equals(userId)) {
            return Result.error("无权查看该记录");
        }

        // 组装返回数据
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", leave.getId());
        detail.put("type", leave.getType());
        detail.put("typeName", TYPE_NAME_MAP.getOrDefault(leave.getType(), leave.getType()));
        detail.put("status", leave.getStatus());
        detail.put("statusName", STATUS_NAME_MAP.getOrDefault(leave.getStatus(), leave.getStatus()));
        detail.put("startDate", leave.getStartDate() != null ? leave.getStartDate().toString() : "");
        detail.put("endDate", leave.getEndDate() != null ? leave.getEndDate().toString() : "");
        detail.put("days", leave.getDays());
        detail.put("reason", leave.getReason());
        detail.put("rejectReason", leave.getRejectReason());
        detail.put("submitTime", leave.getCreatedAt() != null
                ? leave.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "");
        detail.put("approveTime", leave.getApproveTime() != null
                ? leave.getApproveTime().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : null);

        return Result.success(detail);
    }

    // ==================== 教师端 ====================

    /**
     * 教师获取待审批请假列表
     */
    @GetMapping("/teacher/pending")
    @Operation(summary = "教师获取待审批请假列表")
    public Result<Page<Map<String, Object>>> getTeacherPendingLeaves(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        // 校验教师身份
        String role = tokenContextHolder.getCurrentRole();
        if (!"teacher".equals(role)) {
            return Result.error("仅教师可查看");
        }

        // 查询待审批记录
        Page<LeaveRecord> leavePage = leaveRecordService.page(
                new Page<>(page, size),
                new LambdaQueryWrapper<LeaveRecord>()
                        .eq(LeaveRecord::getStatus, "pending")
                        .orderByDesc(LeaveRecord::getCreatedAt));

        return buildTeacherPageResult(leavePage);
    }

    /**
     * 教师获取所有请假列表（可按状态筛选）
     */
    @GetMapping("/teacher/all")
    @Operation(summary = "教师获取所有请假列表")
    public Result<Page<Map<String, Object>>> getTeacherAllLeaves(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        // 校验教师身份
        String role = tokenContextHolder.getCurrentRole();
        if (!"teacher".equals(role)) {
            return Result.error("仅教师可查看");
        }

        // 构建查询条件
        LambdaQueryWrapper<LeaveRecord> wrapper = new LambdaQueryWrapper<LeaveRecord>()
                .orderByDesc(LeaveRecord::getCreatedAt);

        if (status != null && !status.isEmpty()) {
            wrapper.eq(LeaveRecord::getStatus, status);
        }

        Page<LeaveRecord> leavePage = leaveRecordService.page(new Page<>(page, size), wrapper);

        return buildTeacherPageResult(leavePage);
    }

    /**
     * 教师获取请假统计
     */
    @GetMapping("/teacher/stats")
    @Operation(summary = "教师获取请假统计")
    public Result<Map<String, Object>> getTeacherLeaveStats() {
        String role = tokenContextHolder.getCurrentRole();
        if (!"teacher".equals(role)) {
            return Result.error("仅教师可查看");
        }

        // 查询所有记录
        List<LeaveRecord> allLeaves = leaveRecordService.list();

        int total = allLeaves.size();
        int pending = 0;
        int approved = 0;
        int rejected = 0;

        for (LeaveRecord leave : allLeaves) {
            switch (leave.getStatus()) {
                case "pending" -> pending++;
                case "approved" -> approved++;
                case "rejected" -> rejected++;
            }
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("pending", pending);
        stats.put("approved", approved);
        stats.put("rejected", rejected);

        return Result.success(stats);
    }

    /**
     * 教师审批请假
     */
    @PostMapping("/teacher/approve/{id}")
    @Operation(summary = "教师审批请假")
    public Result<String> approveLeave(
            @PathVariable Long id,
            @RequestBody LeaveApproveRequest request) {
        // 校验教师身份
        String role = tokenContextHolder.getCurrentRole();
        if (!"teacher".equals(role)) {
            return Result.error("仅教师可操作");
        }

        Long teacherId = tokenContextHolder.requireCurrentUserId();

        // 查询请假记录
        LeaveRecord leave = leaveRecordService.getById(id);
        if (leave == null) {
            return Result.error("请假记录不存在");
        }

        // 检查是否已审批
        if (!"pending".equals(leave.getStatus())) {
            return Result.error("该请假申请已审批，请勿重复操作");
        }

        if (request.getApproved() != null && request.getApproved()) {
            // 批准
            leave.setStatus("approved");
            leave.setApproveUserId(teacherId);
            leave.setApproveTime(LocalDateTime.now());
            leaveRecordService.updateById(leave);
            // 更新特征数据
            featureDailyService.updateOnLeaveApproved(leave.getUserId(), leave.getStartDate(), leave.getEndDate(), leave.getType());
            log.info("请假 {} 已批准，审批人={}", id, teacherId);
            return Result.success("已批准");
        } else {
            // 驳回
            String rejectReason = request.getRejectReason();
            if (rejectReason == null || rejectReason.trim().isEmpty()) {
                rejectReason = "未通过审批";
            }
            leave.setStatus("rejected");
            leave.setRejectReason(rejectReason);
            leave.setApproveUserId(teacherId);
            leave.setApproveTime(LocalDateTime.now());
            leaveRecordService.updateById(leave);
            log.info("请假 {} 已驳回，审批人={}，原因={}", id, teacherId, rejectReason);
            return Result.success("已驳回");
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 将 LeaveRecord 分页结果组装成教师端需要的格式（含学生姓名、学号）
     */
    private Result<Page<Map<String, Object>>> buildTeacherPageResult(Page<LeaveRecord> leavePage) {
        List<Map<String, Object>> records = leavePage.getRecords().stream().map(leave -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", leave.getId());
            item.put("type", leave.getType());
            item.put("typeName", TYPE_NAME_MAP.getOrDefault(leave.getType(), leave.getType()));
            item.put("status", leave.getStatus());
            item.put("startDate", leave.getStartDate() != null ? leave.getStartDate().toString() : "");
            item.put("endDate", leave.getEndDate() != null ? leave.getEndDate().toString() : "");
            item.put("days", leave.getDays());
            item.put("reason", leave.getReason());
            item.put("rejectReason", leave.getRejectReason());
            item.put("submitTime", leave.getCreatedAt() != null
                    ? leave.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "");
            item.put("approveTime", leave.getApproveTime() != null
                    ? leave.getApproveTime().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : null);

            // 查询学生信息
            if (leave.getUserId() != null) {
                User student = userService.getById(leave.getUserId());
                if (student != null) {
                    item.put("studentName", student.getName());
                    item.put("studentId", student.getStudentId());
                } else {
                    item.put("studentName", "未知");
                    item.put("studentId", "");
                }
            }

            return item;
        }).collect(Collectors.toList());

        Page<Map<String, Object>> pageResult = new Page<>(
                leavePage.getCurrent(), leavePage.getSize(), leavePage.getTotal());
        pageResult.setRecords(records);

        return Result.success(pageResult);
    }
}
