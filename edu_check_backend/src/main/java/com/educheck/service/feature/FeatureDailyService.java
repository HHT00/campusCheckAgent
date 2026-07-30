package com.educheck.service.feature;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.educheck.entity.FeatureDaily;
import com.educheck.mapper.FeatureDailyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeatureDailyService {

    private final FeatureDailyMapper featureDailyMapper;

    // ==================== 实时事件更新 ====================

    /** 上课签到后调用 */
    @Transactional
    public void updateOnClassCheckin(Long userId, LocalDate date, String status) {
        if (userId == null || date == null || status == null) return;

        FeatureDaily feature = getOrCreate(userId, date);

        switch (status) {
            case "present" -> feature.setClassPresent(feature.getClassPresent() == null ? 1 : feature.getClassPresent() + 1);
            case "late"    -> {
                feature.setClassLate(feature.getClassLate() == null ? 1 : feature.getClassLate() + 1);
                feature.setLateLast7d(reCalcLateLast7d(userId, date));
            }
            case "absent"  -> {
                feature.setClassAbsent(feature.getClassAbsent() == null ? 1 : feature.getClassAbsent() + 1);
                feature.setAbsentLast7d(calcAbsentLast7d(userId, date));
            }
            default -> {
                log.warn("未知签到状态: {}, 跳过特征更新", status);
                return;
            }
        }

        saveOrUpdate(feature);
        log.debug("上课签到特征更新: userId={}, date={}, status={}", userId, date, status);
    }

    /** 查寝打卡后调用 */
    @Transactional
    public void updateOnDormCheckin(Long userId, LocalDate date, LocalTime time, String building) {
        if (userId == null || date == null || time == null) return;

        FeatureDaily feature = getOrCreate(userId, date);

        feature.setDormDone(1);
        feature.setDormTime(time);
        feature.setDormBuilding(building);
        feature.setDormIsLate(time.isAfter(LocalTime.of(23, 0)) ? 1 : 0);
        feature.setDormLateLast7d(reCalcDormLateLast7d(userId, date));

        // 从前一天记录复制 prev_dorm_late
        FeatureDaily yesterday = getByDate(userId, date.minusDays(1));
        feature.setPrevDormLate(yesterday != null ? yesterday.getDormIsLate() : 0);

        saveOrUpdate(feature);
        log.debug("查寝特征更新: userId={}, date={}, isLate={}", userId, date, feature.getDormIsLate());
    }

    /** 实习打卡后调用 */
    @Transactional
    public void updateOnInternCheckin(Long userId, LocalDate date) {
        FeatureDaily feature = getOrCreate(userId, date);
        feature.setInternDone(1);
        saveOrUpdate(feature);
        log.debug("实习特征更新: userId={}, date={}", userId, date);
    }

    /** 请假审批通过后调用 */
    @Transactional
    public void updateOnLeaveApproved(Long userId, LocalDate startDate, LocalDate endDate, String type) {
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            FeatureDaily feature = getOrCreate(userId, d);
            feature.setLeaveToday(1);
            saveOrUpdate(feature);
        }
        // 更新滚动统计（近30天请假/周一病假）
        featureDailyMapper.refreshLeaveStats(userId, startDate);
        log.debug("请假特征更新: userId={}, from={}, to={}", userId, startDate, endDate);
    }

    // ==================== 定时校准方法 ====================

    /** 初始化今日空记录（给所有学生） */
    public void initDailyRecord(LocalDate today) {
        int weekDay = today.getDayOfWeek().getValue();
        java.util.List<Long> userIds = featureDailyMapper.getAllStudentIds();
        int inserted = 0;

        for (Long userId : userIds) {
            try {
                FeatureDaily feature = new FeatureDaily();
                feature.setUserId(userId);
                feature.setDate(today);
                feature.setWeekDay(weekDay);
                zeroFields(feature);
                featureDailyMapper.insert(feature);
                inserted++;
            } catch (Exception e) {
                log.debug("今日记录已存在: userId={}", userId);
            }
        }
        log.info("初始化今日记录完成: 新增 {} 条", inserted);
    }

    // ==================== 私有辅助 ====================

    private FeatureDaily getOrCreate(Long userId, LocalDate date) {
        FeatureDaily feature = getByDate(userId, date);
        if (feature != null) return feature;

        feature = new FeatureDaily();
        feature.setUserId(userId);
        feature.setDate(date);
        feature.setWeekDay(date.getDayOfWeek().getValue());
        zeroFields(feature);
        return feature;
    }

    private void zeroFields(FeatureDaily f) {
        f.setClassRequired(0);
        f.setClassPresent(0);
        f.setClassLate(0);
        f.setClassAbsent(0);
        f.setDormDone(0);
        f.setDormIsLate(0);
        f.setInternDone(0);
        f.setLeaveToday(0);
        f.setAbsentLast7d(0);
        f.setPrevDormLate(0);
        f.setIsMorningClass(0);
        f.setStreakDays(0);
        f.setLeaveCount30d(0);
        f.setMondaySick30d(0);
        f.setLateLast7d(0);
        f.setDormLateLast7d(0);
    }

    public FeatureDaily getByDate(Long userId, LocalDate date) {
        LambdaQueryWrapper<FeatureDaily> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FeatureDaily::getUserId, userId)
               .eq(FeatureDaily::getDate, date);
        return featureDailyMapper.selectOne(wrapper);
    }

    private int calcAbsentLast7d(Long userId, LocalDate date) {
        return featureDailyMapper.countAbsentLast7d(userId, date.minusDays(6), date);
    }

    private int reCalcLateLast7d(Long userId, LocalDate date) {
        return featureDailyMapper.countLateLast7d(userId, date.minusDays(6), date);
    }

    private int reCalcDormLateLast7d(Long userId, LocalDate date) {
        return featureDailyMapper.countDormLateLast7d(userId, date.minusDays(6), date);
    }

    private void saveOrUpdate(FeatureDaily feature) {
        if (feature.getId() != null) {
            featureDailyMapper.updateById(feature);
        } else {
            featureDailyMapper.insert(feature);
        }
    }
}
