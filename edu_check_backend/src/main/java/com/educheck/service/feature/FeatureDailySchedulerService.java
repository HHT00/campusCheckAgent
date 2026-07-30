package com.educheck.service.feature;

import com.educheck.mapper.FeatureDailyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * 特征数据计算服务
 * 被 FeatureDailyScheduler（定时）和 DataSyncController（手动）共同调用
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeatureDailySchedulerService {

    private final FeatureDailyMapper featureDailyMapper;
    private final JdbcTemplate jdbcTemplate;

    /** 计算今日应到课程数 */
    public int calcRequiredCourses(LocalDate today) {
        String weekDay = toChineseWeekDay(today.getDayOfWeek());
        if (weekDay == null) return 0;
        int count = featureDailyMapper.countTodayCourses(weekDay);
        jdbcTemplate.update("UPDATE feature_daily SET class_required = ? WHERE date = ?", count, today);
        log.info("今日应到课程数: weekDay={}, count={}", weekDay, count);
        return count;
    }

    /** 计算早课标记 */
    public void calcMorningClass(LocalDate today) {
        String weekDay = toChineseWeekDay(today.getDayOfWeek());
        if (weekDay == null) return;
        boolean hasMorning = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM course WHERE week_day = ? AND status = 1 AND start_time <= '09:00'",
            Integer.class, weekDay) > 0;
        jdbcTemplate.update("UPDATE feature_daily SET is_morning_class = ? WHERE date = ?",
            hasMorning ? 1 : 0, today);
    }

    /** 补全当日请假状态 */
    public int fillLeaveStatus(LocalDate today) {
        return jdbcTemplate.update("""
            UPDATE feature_daily fd
            SET leave_today = 1
            WHERE fd.date = ?
              AND EXISTS (
                SELECT 1 FROM leave_record la
                WHERE la.user_id = fd.user_id
                  AND la.status = 'approved'
                  AND la.start_date <= fd.date
                  AND la.end_date >= fd.date
              )
            """, today);
    }

    /** 刷新 7 天滚动窗口 */
    public void refreshRollingWindow(LocalDate today) {
        jdbcTemplate.update("""
            UPDATE feature_daily fd
            SET absent_last_7d = (
                SELECT COALESCE(COUNT(*), 0) FROM class_checkin cc
                WHERE cc.user_id = fd.user_id AND cc.status = 'absent'
                AND cc.date BETWEEN DATE_SUB(fd.date, INTERVAL 6 DAY) AND fd.date
            ),
            late_last_7d = (
                SELECT COALESCE(COUNT(*), 0) FROM class_checkin cc
                WHERE cc.user_id = fd.user_id AND cc.status = 'late'
                AND cc.date BETWEEN DATE_SUB(fd.date, INTERVAL 6 DAY) AND fd.date
            ),
            dorm_late_last_7d = (
                SELECT COALESCE(COUNT(*), 0) FROM dorm_checkin dc
                WHERE dc.user_id = fd.user_id AND dc.status = 'late'
                AND dc.date BETWEEN DATE_SUB(fd.date, INTERVAL 6 DAY) AND fd.date
            ),
            leave_count_30d = (
                SELECT COALESCE(COUNT(*), 0) FROM leave_record la
                WHERE la.user_id = fd.user_id
                AND la.created_at >= DATE_SUB(fd.date, INTERVAL 30 DAY)
            ),
            monday_sick_30d = (
                SELECT COALESCE(COUNT(*), 0) FROM leave_record la
                WHERE la.user_id = fd.user_id AND la.type = 'sick'
                AND DAYOFWEEK(la.start_date) = 2
                AND la.created_at >= DATE_SUB(fd.date, INTERVAL 30 DAY)
            )
            WHERE fd.date >= DATE_SUB(?, INTERVAL 7 DAY)
            """, today);
    }

    /** 复制前日查寝状态 */
    public void copyPrevDormLate(LocalDate today) {
        jdbcTemplate.update("""
            UPDATE feature_daily fd
            JOIN feature_daily fy ON fy.user_id = fd.user_id
                AND fy.date = DATE_SUB(fd.date, INTERVAL 1 DAY)
            SET fd.prev_dorm_late = fy.dorm_is_late
            WHERE fd.date = ? AND fy.dorm_done = 1
            """, today);
    }

    /** 全量运行所有计算 */
    public void runAll(LocalDate today) {
        calcRequiredCourses(today);
        calcMorningClass(today);
        fillLeaveStatus(today);
        refreshRollingWindow(today);
        copyPrevDormLate(today);
        log.info("特征全量计算完成: date={}", today);
    }

    public String toChineseWeekDay(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "周一"; case TUESDAY -> "周二"; case WEDNESDAY -> "周三";
            case THURSDAY -> "周四"; case FRIDAY -> "周五";
            case SATURDAY -> "周六"; case SUNDAY -> "周日";
        };
    }
}
