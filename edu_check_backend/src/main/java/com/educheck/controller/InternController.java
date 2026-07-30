package com.educheck.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.educheck.common.Result;
import com.educheck.common.TokenContextHolder;
import com.educheck.dto.InternCheckinRequest;
import com.educheck.entity.*;
import com.educheck.mapper.InternPhotoMapper;
import com.educheck.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/intern")
@RequiredArgsConstructor
@Tag(name = "实习打卡", description = "实习打卡接口")
public class InternController {

    private final InternCheckinService internCheckinService;
    private final InternshipService internshipService;
    private final DormCheckinStatsService checkinStatsService;
    private final TokenContextHolder tokenContextHolder;
    private final InternPhotoMapper internPhotoMapper;
    private final com.educheck.service.feature.FeatureDailyService featureDailyService;

    @Value("${face.upload-dir:uploads/face}")
    private String baseUploadDir;

    @GetMapping("/my")
    @Operation(summary = "获取我的实习信息")
    public Result<Internship> myInternship() {
        Long userId = tokenContextHolder.requireCurrentUserId();
        Internship internship = internshipService.lambdaQuery()
                .eq(Internship::getUserId, userId)
                .eq(Internship::getStatus, "active")
                .one();
        if (internship == null) {
            Internship defaultInternship = new Internship();
            defaultInternship.setUserId(userId);
            defaultInternship.setCompany("字节跳动科技有限公司");
            defaultInternship.setRole("后端开发实习生");
            defaultInternship.setTotalDays(90);
            defaultInternship.setCompletedDays(10);
            defaultInternship.setStatus("active");
            internshipService.save(defaultInternship);
            return Result.success(defaultInternship);
        }
        return Result.success(internship);
    }

    @GetMapping("/today-status")
    @Operation(summary = "获取今日实习打卡状态")
    public Result<InternCheckin> todayStatus() {
        Long userId = tokenContextHolder.requireCurrentUserId();
        InternCheckin checkin = internCheckinService.lambdaQuery()
                .eq(InternCheckin::getUserId, userId)
                .eq(InternCheckin::getCheckinDate, LocalDate.now())
                .one();
        return Result.success(checkin);
    }

    @GetMapping("/stats")
    @Operation(summary = "获取实习统计")
    public Result<Map<String, Object>> stats() {
        Long userId = tokenContextHolder.requireCurrentUserId();
        Internship internship = internshipService.lambdaQuery()
                .eq(Internship::getUserId, userId)
                .eq(Internship::getStatus, "active")
                .one();

        Map<String, Object> data = new HashMap<>();
        if (internship != null) {
            data.put("company", internship.getCompany());
            data.put("role", internship.getRole());
            int completed = internship.getCompletedDays() != null ? internship.getCompletedDays() : 0;
            int total = internship.getTotalDays() != null ? internship.getTotalDays() : 90;
            int progress = total > 0 ? (int) ((double) completed / total * 100) : 0;
            data.put("progress", progress);
            data.put("completedDays", completed);
            data.put("totalDays", total);
        } else {
            data.put("company", "未分配");
            data.put("role", "未分配");
            data.put("progress", 0);
            data.put("completedDays", 0);
            data.put("totalDays", 90);
        }
        return Result.success(data);
    }

    @PostMapping("/checkin")
    @Operation(summary = "实习打卡")
    public Result<Map<String, Object>> checkin(@RequestBody InternCheckinRequest request) {
        Long userId = tokenContextHolder.requireCurrentUserId();

        if (request.getFaceVerified() == null || !request.getFaceVerified()) {
            return Result.error("请先完成人脸验证");
        }

        Internship internship = internshipService.lambdaQuery()
                .eq(Internship::getUserId, userId)
                .eq(Internship::getStatus, "active")
                .one();
        if (internship == null) {
            return Result.error("未找到实习信息");
        }

        LocalDate today = LocalDate.now();

        InternCheckin existing = internCheckinService.lambdaQuery()
                .eq(InternCheckin::getUserId, userId)
                .eq(InternCheckin::getCheckinDate, today)
                .one();
        if (existing != null) {
            existing.setCheckinTime(LocalDateTime.now());
            existing.setLocationAddr(request.getLocationAddr());
            existing.setLogContent(request.getLogContent());
            existing.setFaceVerified(1);
            internCheckinService.updateById(existing);
            Map<String, Object> res = new HashMap<>();
            res.put("id", existing.getId());
            res.put("dayNumber", existing.getDayNumber());
            res.put("message", "更新打卡成功");
            return Result.success(res);
        }

        int nextDay = (internship.getCompletedDays() != null ? internship.getCompletedDays() : 10) + 1;

        InternCheckin checkin = new InternCheckin();
        checkin.setUserId(userId);
        checkin.setInternshipId(internship.getId());
        checkin.setCheckinDate(today);
        checkin.setCheckinTime(LocalDateTime.now());
        checkin.setDayNumber(nextDay);
        checkin.setLocationAddr(request.getLocationAddr());
        checkin.setLogContent(request.getLogContent());
        checkin.setFaceVerified(1);
        checkin.setStatus("normal");
        internCheckinService.save(checkin);

        // 更新特征数据
        featureDailyService.updateOnInternCheckin(userId, today);

        internship.setCompletedDays(nextDay);
        internshipService.updateById(internship);

        DormCheckinStats stats = checkinStatsService.lambdaQuery()
                .eq(DormCheckinStats::getUserId, userId).one();
        if (stats != null) {
            stats.setInternTotal(stats.getInternTotal() != null ? stats.getInternTotal() + 1 : 1);
            checkinStatsService.updateById(stats);
        } else {
            DormCheckinStats newStats = new DormCheckinStats();
            newStats.setUserId(userId);
            newStats.setDormTotal(0);
            newStats.setClassTotal(0);
            newStats.setInternTotal(1);
            newStats.setStreakDays(0);
            newStats.setTotalPoints(0);
            checkinStatsService.save(newStats);
        }

        Map<String, Object> res = new HashMap<>();
        res.put("id", checkin.getId());
        res.put("dayNumber", nextDay);
        res.put("message", "打卡成功");
        return Result.success(res);
    }

    @GetMapping("/history")
    @Operation(summary = "获取实习打卡记录")
    public Result<Page<Map<String, Object>>> history(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = tokenContextHolder.requireCurrentUserId();

        Page<InternCheckin> checkinPage = internCheckinService.page(
                new Page<>(page, size),
                new LambdaQueryWrapper<InternCheckin>()
                        .eq(InternCheckin::getUserId, userId)
                        .orderByDesc(InternCheckin::getCheckinDate, InternCheckin::getCheckinTime)
        );

        List<Map<String, Object>> records = checkinPage.getRecords().stream().map(c -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", c.getId());
            item.put("day", c.getDayNumber());
            item.put("date", c.getCheckinDate() != null ? c.getCheckinDate().format(DateTimeFormatter.ofPattern("MM/dd")) : "");
            item.put("time", c.getCheckinTime() != null ? c.getCheckinTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "");
            item.put("log", c.getLogContent() != null ? c.getLogContent() : "");
            item.put("status", "已完成");
            // 查询关联的照片
            List<InternPhoto> photos = internPhotoMapper.selectList(
                    new LambdaQueryWrapper<InternPhoto>()
                            .eq(InternPhoto::getCheckinId, c.getId())
            );
            List<String> photoUrls = photos.stream()
                    .map(p -> "http://localhost:8080/" + p.getPhotoUrl())
                    .collect(Collectors.toList());
            item.put("photos", photoUrls);
            return item;
        }).collect(Collectors.toList());

        Page<Map<String, Object>> pageResult = new Page<>(
                checkinPage.getCurrent(),
                checkinPage.getSize(),
                checkinPage.getTotal()
        );
        pageResult.setRecords(records);
        return Result.success(pageResult);
    }

    @PostMapping("/photo/upload")
    @Operation(summary = "上传实习照片（base64）")
    public Result<Map<String, Object>> uploadPhoto(@RequestBody Map<String, Object> body) {
        Long userId = tokenContextHolder.requireCurrentUserId();

        Object checkinIdObj = body.get("checkinId");
        String imageBase64 = (String) body.get("image");

        if (checkinIdObj == null) {
            return Result.error("checkinId不能为空");
        }
        if (imageBase64 == null || imageBase64.isEmpty()) {
            return Result.error("图片不能为空");
        }

        Long checkinId = Long.valueOf(checkinIdObj.toString());

        try {
            Path jobsDir = Paths.get(baseUploadDir).getParent().resolve("jobs");
            if (!Files.exists(jobsDir)) {
                Files.createDirectories(jobsDir);
            }

            String fileName = "intern_" + checkinId + "_" + UUID.randomUUID().toString().substring(0, 8) + ".jpg";
            Path filePath = jobsDir.resolve(fileName);
            byte[] imageBytes = Base64.getDecoder().decode(imageBase64);
            Files.write(filePath, imageBytes);

            // 入库 intern_photo
            InternPhoto photo = new InternPhoto();
            photo.setCheckinId(checkinId);
            photo.setPhotoUrl("uploads/jobs/" + fileName);
            internPhotoMapper.insert(photo);

            Map<String, Object> data = new HashMap<>();
            data.put("id", photo.getId());
            data.put("url", photo.getPhotoUrl());
            return Result.success("上传成功", data);
        } catch (IllegalArgumentException e) {
            return Result.error("图片base64数据格式错误");
        } catch (IOException e) {
            log.error("实习照片上传失败", e);
            return Result.error("文件上传失败");
        }
    }

    @DeleteMapping("/photo/{id}")
    @Operation(summary = "删除实习照片")
    public Result<Void> deletePhoto(@PathVariable Long id) {
        InternPhoto photo = internPhotoMapper.selectById(id);
        if (photo == null) {
            return Result.error("照片不存在");
        }

        // 删磁盘文件
        try {
            Path filePath = Paths.get(baseUploadDir).getParent().resolve(photo.getPhotoUrl());
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.warn("删除实习照片文件失败: {}", e.getMessage());
        }

        // 删数据库记录
        internPhotoMapper.deleteById(id);
        return Result.success("删除成功", null);
    }
}
