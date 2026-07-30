package com.educheck.task;

import com.educheck.entity.DormCheckin;
import com.educheck.entity.User;
import com.educheck.service.DormCheckinService;
import com.educheck.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class DormCheckinScheduler {

    private final DormCheckinService dormCheckinService;
    private final UserService userService;

    /**
     * 每天凌晨 00:30 执行
     * 自动为前一天未查寝的学生生成 absent 记录
     */
    @Scheduled(cron = "0 30 0 * * ?")
    public void autoFillAbsent() {
        LocalDate yesterday = LocalDate.now().minusDays(1);

        // 1. 获取所有学生ID
        List<Long> allStudentIds = userService.lambdaQuery()
                .eq(User::getRole, "student")
                .list()
                .stream()
                .map(User::getId)
                .collect(Collectors.toList());

        if (allStudentIds.isEmpty()) {
            return;
        }

        // 2. 查询昨天已打卡的学生ID
        Set<Long> checkedUserIds = dormCheckinService.lambdaQuery()
                .eq(DormCheckin::getDate, yesterday)
                .list()
                .stream()
                .map(DormCheckin::getUserId)
                .collect(Collectors.toSet());

        // 3. 找出未打卡的学生
        List<DormCheckin> absentRecords = allStudentIds.stream()
                .filter(id -> !checkedUserIds.contains(id))
                .map(id -> {
                    DormCheckin record = new DormCheckin();
                    record.setUserId(id);
                    record.setDate(yesterday);
                    record.setCheckinTime(yesterday.atTime(23, 59, 59));
                    record.setStatus("absent");
                    return record;
                })
                .collect(Collectors.toList());

        // 4. 批量插入
        if (!absentRecords.isEmpty()) {
            dormCheckinService.saveBatch(absentRecords);
            log.info("自动补全查寝未归记录 {} 条，日期: {}", absentRecords.size(), yesterday);
        }
    }
}