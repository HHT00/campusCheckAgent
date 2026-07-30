package com.educheck.service.feature;

import com.educheck.entity.AlertEvent;
import com.educheck.mapper.AlertEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 预警规则引擎
 * 每天凌晨 03:30 执行，扫描 feature_daily 按规则生成 alert_event
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertRuleEngine {

    private final JdbcTemplate jdbc;
    private final AlertEventMapper alertEventMapper;

    /** 03:30 执行规则检查 */
    @Scheduled(cron = "0 30 3 * * ?")
    public void execute() {
        LocalDate today = LocalDate.now();
        checkContinuousAbsent(today);
        checkDormLatePattern(today);
        checkLeaveAbnormal(today);
        checkInternBreak(today);
        log.info("预警规则引擎执行完毕");
    }

    /** 规则1: 连续旷课 ≥ 3 天（同一学生近 7 天旷课数） */
    private void checkContinuousAbsent(LocalDate today) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT fd.user_id, u.name, u.student_id, fd.absent_last_7d
            FROM feature_daily fd
            JOIN user u ON u.id = fd.user_id
            WHERE fd.date = ? AND fd.absent_last_7d >= 3
            """, today);

        for (Map<String, Object> row : rows) {
            Long userId = ((Number) row.get("user_id")).longValue();
            int count = ((Number) row.get("absent_last_7d")).intValue();
            String name = (String) row.get("name");

            // 避免重复生成（当天已有同类型未处理的预警）
            boolean exists = alertEventMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AlertEvent>()
                    .eq(AlertEvent::getUserId, userId)
                    .eq(AlertEvent::getAlertType, "continuous_absent")
                    .eq(AlertEvent::getResolved, 0)
                    .ge(AlertEvent::getCreatedAt, LocalDate.now().atStartOfDay())
            ) > 0;
            if (exists) continue;

            AlertEvent alert = new AlertEvent();
            alert.setUserId(userId);
            alert.setAlertType("continuous_absent");
            alert.setRiskLevel("high");
            alert.setTitle("连续旷课预警");
            alert.setDetail(String.format("学生 %s 近7天旷课 %d 次，请关注", name, count));
            alert.setNotified(0);
            alert.setResolved(0);
            alert.setCreatedAt(LocalDateTime.now());
            alertEventMapper.insert(alert);
            log.warn("预警: 连续旷课 userId={}, name={}, count={}", userId, name, count);
        }
    }

    /** 规则2: 晚归 + 次日有早课 */
    private void checkDormLatePattern(LocalDate today) {
        // 检查昨天的记录：昨晚晚归 且 今天有早课
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT fd.user_id, u.name
            FROM feature_daily fd
            JOIN user u ON u.id = fd.user_id
            WHERE fd.date = ? AND fd.dorm_is_late = 1 AND fd.is_morning_class = 1
            """, today);

        for (Map<String, Object> row : rows) {
            Long userId = ((Number) row.get("user_id")).longValue();
            String name = (String) row.get("name");

            boolean exists = alertEventMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AlertEvent>()
                    .eq(AlertEvent::getUserId, userId)
                    .eq(AlertEvent::getAlertType, "dorm_late_morning")
                    .ge(AlertEvent::getCreatedAt, LocalDate.now().minusDays(1).atStartOfDay())
            ) > 0;
            if (exists) continue;

            AlertEvent alert = new AlertEvent();
            alert.setUserId(userId);
            alert.setAlertType("dorm_late_morning");
            alert.setRiskLevel("mid");
            alert.setTitle("晚归后次日有早课");
            alert.setDetail(String.format("学生 %s 昨晚晚归，今日有早课，建议关注出勤", name));
            alert.setNotified(0);
            alert.setResolved(0);
            alert.setCreatedAt(LocalDateTime.now());
            alertEventMapper.insert(alert);
            log.warn("预警: 晚归+早课 userId={}, name={}", userId, name);
        }
    }

    /** 规则3: 请假模式异常（近 30 天周一病假 ≥ 3 次） */
    private void checkLeaveAbnormal(LocalDate today) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT fd.user_id, u.name, fd.monday_sick_30d
            FROM feature_daily fd
            JOIN user u ON u.id = fd.user_id
            WHERE fd.date = ? AND fd.monday_sick_30d >= 3
            """, today);

        for (Map<String, Object> row : rows) {
            Long userId = ((Number) row.get("user_id")).longValue();
            int count = ((Number) row.get("monday_sick_30d")).intValue();
            String name = (String) row.get("name");

            boolean exists = alertEventMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AlertEvent>()
                    .eq(AlertEvent::getUserId, userId)
                    .eq(AlertEvent::getAlertType, "leave_abnormal")
                    .eq(AlertEvent::getResolved, 0)
                    .ge(AlertEvent::getCreatedAt, LocalDate.now().minusDays(7).atStartOfDay())
            ) > 0;
            if (exists) continue;

            AlertEvent alert = new AlertEvent();
            alert.setUserId(userId);
            alert.setAlertType("leave_abnormal");
            alert.setRiskLevel("mid");
            alert.setTitle("请假模式异常");
            alert.setDetail(String.format("学生 %s 近30天有 %d 次周一病假记录，疑似虚假请假", name, count));
            alert.setNotified(0);
            alert.setResolved(0);
            alert.setCreatedAt(LocalDateTime.now());
            alertEventMapper.insert(alert);
            log.warn("预警: 请假异常 userId={}, name={}, mondaySick={}", userId, name, count);
        }
    }

    /** 规则4: 实习断签（连续 3 天无实习打卡） */
    private void checkInternBreak(LocalDate today) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT DISTINCT i.user_id, u.name
            FROM internship i
            JOIN user u ON u.id = i.user_id
            WHERE i.status = 'active'
              AND NOT EXISTS (
                SELECT 1 FROM intern_checkin ic
                WHERE ic.user_id = i.user_id
                  AND ic.checkin_date >= DATE_SUB(?, INTERVAL 3 DAY)
              )
            """, today);

        for (Map<String, Object> row : rows) {
            Long userId = ((Number) row.get("user_id")).longValue();
            String name = (String) row.get("name");

            boolean exists = alertEventMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AlertEvent>()
                    .eq(AlertEvent::getUserId, userId)
                    .eq(AlertEvent::getAlertType, "intern_break")
                    .eq(AlertEvent::getResolved, 0)
                    .ge(AlertEvent::getCreatedAt, LocalDate.now().minusDays(3).atStartOfDay())
            ) > 0;
            if (exists) continue;

            AlertEvent alert = new AlertEvent();
            alert.setUserId(userId);
            alert.setAlertType("intern_break");
            alert.setRiskLevel("mid");
            alert.setTitle("实习断签预警");
            alert.setDetail(String.format("学生 %s 已连续3天未实习打卡", name));
            alert.setNotified(0);
            alert.setResolved(0);
            alert.setCreatedAt(LocalDateTime.now());
            alertEventMapper.insert(alert);
            log.warn("预警: 实习断签 userId={}, name={}", userId, name);
        }
    }
}
