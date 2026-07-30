package com.educheck.agent;

import com.educheck.entity.*;
import com.educheck.service.*;
import com.educheck.service.feature.FeatureDailyService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentTools {

    private final CourseService courseService;
    private final LeaveRecordService leaveRecordService;
    private final FeatureDailyService featureDailyService;
    private final UserService userService;
    private final NotificationService notificationService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public static final ThreadLocal<Long> currentUserId = new ThreadLocal<>();
    public static final ThreadLocal<String> currentUserRole = new ThreadLocal<>();
    public static final ThreadLocal<String> currentUserName = new ThreadLocal<>();

    public static void clearUserId() { currentUserId.remove(); currentUserRole.remove(); currentUserName.remove(); }

    // ==================== 课表查询 ====================

    @Tool("查询指定日期的课程安排。任何人都可以使用。date 格式为 yyyy-MM-dd，例如 2026-07-30。返回该日所有课程的名称、时间、地点、授课教师。")
    public String querySchedule(@P("日期，必须使用 yyyy-MM-dd 格式，例如 2026-07-30") String dateStr) {
        LocalDate date;
        try { date = LocalDate.parse(dateStr); } catch (Exception e) {
            return "日期格式错误，请使用 yyyy-MM-dd 格式，例如 2026-07-30";
        }
        String weekDay = switch (date.getDayOfWeek()) {
            case MONDAY -> "周一"; case TUESDAY -> "周二"; case WEDNESDAY -> "周三";
            case THURSDAY -> "周四"; case FRIDAY -> "周五";
            case SATURDAY -> "周六"; case SUNDAY -> "周日";
        };
        List<Course> courses = courseService.lambdaQuery()
                .eq(Course::getWeekDay, weekDay).eq(Course::getStatus, 1)
                .orderByAsc(Course::getStartTime).list();
        if (courses.isEmpty()) return dateStr + " (" + weekDay + ") 没有课程安排。";
        StringBuilder sb = new StringBuilder(dateStr + " (" + weekDay + ") 的课程安排：\n");
        for (int i = 0; i < courses.size(); i++) {
            Course c = courses.get(i);
            sb.append(i + 1).append(". ").append(c.getName())
              .append(" ").append(c.getStartTime()).append("-").append(c.getEndTime())
              .append(" ").append(c.getLocation()).append(" ").append(c.getTeacher()).append("\n");
        }
        return sb.toString();
    }

    // ==================== 考勤查询 ====================

    @Tool("查询当前登录学生本人的考勤数据（仅限本人）。range: 7d=近7天统计, 30d=近30天统计。返回旷课、迟到、晚归、连续打卡天数和请假次数。")
    public String queryMyAttendance(@P("查询范围：7d 或 30d") String range) {
        Long userId = currentUserId.get();
        if (userId == null) return "无法获取当前用户信息";
        FeatureDaily feature = featureDailyService.getByDate(userId, LocalDate.now());
        if (feature == null) return "暂无考勤数据。";
        return String.format(
            "近7天考勤：\n- 旷课 %d 次\n- 迟到 %d 次\n- 晚归 %d 次\n- 连续打卡 %d 天\n- 近30天请假 %d 次（其中周一病假 %d 次）",
            feature.getAbsentLast7d() != null ? feature.getAbsentLast7d() : 0,
            feature.getLateLast7d() != null ? feature.getLateLast7d() : 0,
            feature.getDormLateLast7d() != null ? feature.getDormLateLast7d() : 0,
            feature.getStreakDays() != null ? feature.getStreakDays() : 0,
            feature.getLeaveCount30d() != null ? feature.getLeaveCount30d() : 0,
            feature.getMondaySick30d() != null ? feature.getMondaySick30d() : 0
        );
    }

    @Tool("【仅限教师角色使用】查询指定学生的考勤数据。传入学生ID（数字），返回该学生的旷课、迟到、晚归次数。如果当前用户不是教师，会返回权限错误。")
    public String queryStudentAttendance(@P("要查询的学生ID，必须是数字") Long studentId, @P("查询范围：7d 或 30d") String range) {
        if (!"teacher".equals(currentUserRole.get())) return "权限不足：仅教师可查询其他学生的考勤数据。";
        FeatureDaily feature = featureDailyService.getByDate(studentId, LocalDate.now());
        User student = userService.getById(studentId);
        String name = student != null ? student.getName() : "未知";
        if (feature == null) return "学生 " + name + " 暂无考勤数据。";
        return String.format("学生 %s 近7天考勤：\n- 旷课 %d 次\n- 迟到 %d 次\n- 晚归 %d 次",
            name,
            feature.getAbsentLast7d() != null ? feature.getAbsentLast7d() : 0,
            feature.getLateLast7d() != null ? feature.getLateLast7d() : 0,
            feature.getDormLateLast7d() != null ? feature.getDormLateLast7d() : 0);
    }

    // ==================== 提交请假 ====================

    @Tool("为当前登录学生提交请假申请。type: sick(病假)/personal(事假)/official(公假), startDate/endDate 格式 yyyy-MM-dd, reason 为请假事由。提交后状态为「待审批」，等待教师审批。")
    public String submitLeave(
            @P("请假类型：sick=病假, personal=事假, official=公假") String type,
            @P("开始日期 yyyy-MM-dd") String startDate,
            @P("结束日期 yyyy-MM-dd") String endDate,
            @P("请假事由，如「发烧需要休息」") String reason) {
        Long userId = currentUserId.get();
        if (userId == null) return "无法获取当前用户信息";
        LocalDate start, end;
        try { start = LocalDate.parse(startDate); end = LocalDate.parse(endDate); } catch (Exception e) {
            return "日期格式错误，请使用 yyyy-MM-dd";
        }
        LeaveRecord leave = new LeaveRecord();
        leave.setUserId(userId);
        leave.setType(type);
        leave.setStartDate(start);
        leave.setEndDate(end);
        leave.setDays((int) java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1);
        leave.setReason(reason);
        leave.setStatus("pending");
        leaveRecordService.save(leave);
        return String.format("请假已提交，等待审批。编号: %d，类型: %s，时间: %s 至 %s，共%d天",
            leave.getId(), type, startDate, endDate, leave.getDays());
    }

    // ==================== 通知教师 ====================

    @Tool("请假提交后，用此工具通知教师有新的请假申请需要审批。teacherId 为教师ID（教师账号ID=2），leaveId 为请假编号。通知后教师端会收到提醒。")
    public String notifyTeacher(@P("教师ID，例如教师账号ID为2") Long teacherId, @P("请假编号") Long leaveId) {
        String studentName = currentUserName.get();
        Notification notification = new Notification();
        notification.setUserId(teacherId);
        notification.setTitle("请假审批通知");
        notification.setContent(String.format("学生 %s 提交了请假申请（编号: %d），请及时审批。", studentName, leaveId));
        notification.setType("leave");
        notification.setIsRead(0);
        notification.setSourceId(leaveId);
        notificationService.save(notification);
        return "已通知教师。";
    }

    // ==================== 教师审批请假 ====================

    @Tool("【仅限教师角色使用】审批请假申请。leaveId 为请假编号，approved=true 表示批准，approved=false 表示驳回（此时需填写 rejectReason）。如果当前用户不是教师，会返回权限错误。")
    public String approveLeave(
            @P("请假编号") Long leaveId,
            @P("是否批准：true=批准, false=驳回") Boolean approved,
            @P("驳回原因，批准时可为空") String rejectReason) {
        if (!"teacher".equals(currentUserRole.get())) return "权限不足：仅教师可审批请假。";
        LeaveRecord leave = leaveRecordService.getById(leaveId);
        if (leave == null) return "请假记录不存在";
        if (!"pending".equals(leave.getStatus())) return "该请假已审批";
        Long teacherId = currentUserId.get();
        if (Boolean.TRUE.equals(approved)) {
            leave.setStatus("approved"); leave.setApproveUserId(teacherId);
            leave.setApproveTime(java.time.LocalDateTime.now());
            leaveRecordService.updateById(leave);
            return "请假 " + leaveId + " 已批准。";
        } else {
            if (rejectReason == null || rejectReason.trim().isEmpty()) rejectReason = "未通过审批";
            leave.setStatus("rejected"); leave.setRejectReason(rejectReason);
            leave.setApproveUserId(teacherId);
            leave.setApproveTime(java.time.LocalDateTime.now());
            leaveRecordService.updateById(leave);
            return "请假 " + leaveId + " 已驳回。原因：" + rejectReason;
        }
    }

    // ==================== 查看待审批请假 ====================

    @Tool("【仅限教师角色使用】查看所有待审批的请假申请列表，返回每条申请的编号、学生姓名、请假类型、时间、事由。教师可以用编号去审批。")
    public String queryPendingLeaves() {
        if (!"teacher".equals(currentUserRole.get())) return "权限不足：仅教师可查看";
        List<java.util.Map<String, Object>> list = jdbcTemplate.queryForList(
            "SELECT lr.id, lr.type, lr.start_date, lr.end_date, lr.days, lr.reason, lr.created_at, u.name as student_name, u.student_id FROM leave_record lr JOIN user u ON u.id = lr.user_id WHERE lr.status = 'pending' ORDER BY lr.created_at DESC");
        if (list.isEmpty()) return "暂无待审批的请假申请。";
        StringBuilder sb = new StringBuilder("待审批的请假申请（共" + list.size() + "条）：\n");
        for (java.util.Map<String, Object> l : list)
            sb.append("【编号").append(l.get("id")).append("】").append(l.get("student_name"))
              .append("(").append(l.get("student_id")).append(") ").append(l.get("type")).append("假 ")
              .append(l.get("start_date")).append("~").append(l.get("end_date")).append(" ").append(l.get("days")).append("天\n");
        return sb.toString();
    }

    // ==================== 查询预警 ====================

    @Tool("查询系统预警列表。如果当前用户是学生，只返回自己的预警；如果是教师，返回所有未处理的预警。预警包括连续旷课、晚归等异常行为通知。")
    public String queryAlerts() {
        Long userId = currentUserId.get();
        if (userId == null) return "无法获取当前用户信息";
        boolean isTeacher = "teacher".equals(currentUserRole.get());
        String sql = isTeacher
            ? "SELECT a.id, a.alert_type, a.risk_level, a.title, a.detail, a.created_at, u.name as student_name FROM alert_event a JOIN user u ON u.id = a.user_id WHERE a.resolved = 0 ORDER BY a.created_at DESC LIMIT 20"
            : "SELECT alert_type, risk_level, title, detail, created_at FROM alert_event WHERE user_id = ? AND resolved = 0 ORDER BY created_at DESC LIMIT 5";
        List<java.util.Map<String, Object>> alerts = isTeacher
            ? jdbcTemplate.queryForList(sql)
            : jdbcTemplate.queryForList(sql, userId);
        if (alerts.isEmpty()) return "暂无未处理的预警。";
        StringBuilder sb = new StringBuilder("未处理的预警（共" + alerts.size() + "条）：\n");
        for (int i = 0; i < alerts.size(); i++) {
            sb.append(i + 1).append(". ").append(alerts.get(i).get("title")).append("\n");
            if (alerts.get(i).containsKey("student_name"))
                sb.append("   学生：").append(alerts.get(i).get("student_name")).append("\n");
        }
        return sb.toString();
    }
}
