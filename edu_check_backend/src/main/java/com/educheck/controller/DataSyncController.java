package com.educheck.controller;

import com.educheck.common.Result;
import com.educheck.service.feature.AlertRuleEngine;
import com.educheck.service.feature.FeatureDailyService;
import com.educheck.service.feature.FeatureDailySchedulerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/sync")
@RequiredArgsConstructor
@Tag(name = "数据同步", description = "手动触发数据采集与聚合计算")
public class DataSyncController {

    private final FeatureDailyService featureDailyService;
    private final FeatureDailySchedulerService schedulerService;
    private final AlertRuleEngine alertRuleEngine;
    private final com.educheck.agent.KnowledgeBaseRag knowledgeBaseRag;

    @PostMapping("/all")
    @Operation(summary = "手动触发全量数据同步（点击后立即统计）")
    public Result<Map<String, Object>> syncAll() {
        LocalDate today = LocalDate.now();
        Map<String, Object> report = new LinkedHashMap<>();
        long start = System.currentTimeMillis();

        try {
            featureDailyService.initDailyRecord(today);
            report.put("initDailyRecord", "OK");
        } catch (Exception e) {
            report.put("initDailyRecord", "SKIP: " + e.getMessage());
        }

        try {
            int count = schedulerService.calcRequiredCourses(today);
            report.put("calcRequiredCourses", count + " 门课");
        } catch (Exception e) {
            report.put("calcRequiredCourses", "SKIP: " + e.getMessage());
        }

        try {
            schedulerService.calcMorningClass(today);
            report.put("calcMorningClass", "OK");
        } catch (Exception e) {
            report.put("calcMorningClass", "SKIP: " + e.getMessage());
        }

        try {
            int updated = schedulerService.fillLeaveStatus(today);
            report.put("fillLeaveStatus", updated + " 条请假");
        } catch (Exception e) {
            report.put("fillLeaveStatus", "SKIP: " + e.getMessage());
        }

        try {
            schedulerService.refreshRollingWindow(today);
            report.put("refreshRollingWindow", "OK");
        } catch (Exception e) {
            report.put("refreshRollingWindow", "SKIP: " + e.getMessage());
        }

        try {
            schedulerService.copyPrevDormLate(today);
            report.put("copyPrevDormLate", "OK");
        } catch (Exception e) {
            report.put("copyPrevDormLate", "SKIP: " + e.getMessage());
        }

        try {
            alertRuleEngine.execute();
            report.put("alertRuleEngine", "OK");
        } catch (Exception e) {
            report.put("alertRuleEngine", "SKIP: " + e.getMessage());
        }

        long elapsed = System.currentTimeMillis() - start;
        report.put("elapsed", elapsed + "ms");
        log.info("手动数据同步完成: {}ms", elapsed);

        return Result.success(report);
    }

    @PostMapping("/alerts")
    @Operation(summary = "手动触发预警规则计算")
    public Result<String> syncAlerts() {
        alertRuleEngine.execute();
        return Result.success("预警规则已执行");
    }

    @PostMapping("/vectors")
    @Operation(summary = "全量重建知识库向量索引（使用 LangChain4j RAG）")
    public Result<String> rebuildVectors() {
        knowledgeBaseRag.rebuild();
        return Result.success("知识库索引已重建");
    }
}
