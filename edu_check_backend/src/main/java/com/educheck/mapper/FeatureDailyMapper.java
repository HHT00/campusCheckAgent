package com.educheck.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educheck.entity.FeatureDaily;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;

public interface FeatureDailyMapper extends BaseMapper<FeatureDaily> {

    /** 统计近7天旷课数 */
    @Select("SELECT COALESCE(COUNT(*), 0) FROM class_checkin " +
            "WHERE user_id = #{userId} AND status = 'absent' " +
            "AND date BETWEEN #{startDate} AND #{endDate}")
    int countAbsentLast7d(@Param("userId") Long userId,
                          @Param("startDate") LocalDate startDate,
                          @Param("endDate") LocalDate endDate);

    /** 统计近7天迟到数 */
    @Select("SELECT COALESCE(COUNT(*), 0) FROM class_checkin " +
            "WHERE user_id = #{userId} AND status = 'late' " +
            "AND date BETWEEN #{startDate} AND #{endDate}")
    int countLateLast7d(@Param("userId") Long userId,
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate);

    /** 统计近7天晚归数 */
    @Select("SELECT COALESCE(COUNT(*), 0) FROM dorm_checkin " +
            "WHERE user_id = #{userId} AND status = 'late' " +
            "AND date BETWEEN #{startDate} AND #{endDate}")
    int countDormLateLast7d(@Param("userId") Long userId,
                            @Param("startDate") LocalDate startDate,
                            @Param("endDate") LocalDate endDate);

    /** 刷新近30天请假统计 */
    @Update("UPDATE feature_daily fd " +
            "SET leave_count_30d = (SELECT COALESCE(COUNT(*), 0) FROM leave_application " +
            "    WHERE user_id = #{userId} AND created_at >= DATE_SUB(#{date}, INTERVAL 30 DAY)), " +
            "    monday_sick_30d = (SELECT COALESCE(COUNT(*), 0) FROM leave_application " +
            "    WHERE user_id = #{userId} AND type = 'sick' " +
            "    AND DAYOFWEEK(start_date) = 2 " +
            "    AND created_at >= DATE_SUB(#{date}, INTERVAL 30 DAY)) " +
            "WHERE user_id = #{userId} AND date = #{date}")
    void refreshLeaveStats(@Param("userId") Long userId, @Param("date") LocalDate date);

    /** 统计某天应到课程数 */
    @Select("SELECT COUNT(*) FROM course WHERE week_day = #{weekDay} AND status = 1")
    int countTodayCourses(@Param("weekDay") String weekDay);

    /** 获取所有活跃学生ID */
    @Select("SELECT id FROM user WHERE role = 'student' AND status = 1")
    List<Long> getAllStudentIds();
}
