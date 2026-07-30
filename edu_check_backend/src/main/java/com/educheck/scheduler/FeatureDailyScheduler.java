package com.educheck.scheduler;

import com.educheck.service.feature.FeatureDailySchedulerService;
import com.educheck.service.feature.FeatureDailyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class FeatureDailyScheduler {

    private final FeatureDailyService featureDailyService;
    private final FeatureDailySchedulerService schedulerService;

    @Scheduled(cron = "0 30 0 * * ?")
    public void initDailyRecord() {
        featureDailyService.initDailyRecord(LocalDate.now());
    }

    @Scheduled(cron = "0 0 1 * * ?")
    public void calcRequiredCourses() {
        schedulerService.calcRequiredCourses(LocalDate.now());
    }

    @Scheduled(cron = "0 30 1 * * ?")
    public void calcMorningClass() {
        schedulerService.calcMorningClass(LocalDate.now());
    }

    @Scheduled(cron = "0 0 2 * * ?")
    public void fillLeaveStatus() {
        schedulerService.fillLeaveStatus(LocalDate.now());
    }

    @Scheduled(cron = "0 30 2 * * ?")
    public void refreshRollingWindow() {
        schedulerService.refreshRollingWindow(LocalDate.now());
    }

    @Scheduled(cron = "0 0 3 * * ?")
    public void copyPrevDormLate() {
        schedulerService.copyPrevDormLate(LocalDate.now());
    }
}
