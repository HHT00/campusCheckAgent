package com.educheck.controller;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.educheck.common.Result;
import com.educheck.common.TokenContextHolder;
import com.educheck.dto.ClassCheckinRequest;
import com.educheck.entity.ClassCheckin;
import com.educheck.entity.Course;
import com.educheck.entity.DormCheckinStats;
import com.educheck.service.ClassCheckinService;
import com.educheck.service.CourseService;
import com.educheck.service.DormCheckinStatsService;
import com.educheck.service.feature.FeatureDailyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.time.format.DateTimeFormatter;
import java.util.*;


//接口返回的数据格式为json
@RestController
//定义当前控制的处理的接口
@RequestMapping("/api/course")

//创建service对象
//给所有的final修饰的成员变量 创建对应的对象
@RequiredArgsConstructor

//
@Tag(name="上课打卡" , description = "上课打卡 /api/course")

public class CourseCheckinController {
    // 创建需要的service对象
    private final CourseService courseService;
    private final ClassCheckinService classCheckinService;
    private final TokenContextHolder tokenContextHolder;

    // 创建打卡统计service
    private final DormCheckinStatsService dormcheckinStatsService;
    private final FeatureDailyService featureDailyService;

    @GetMapping("/today")
    @Operation(summary = "获取今天的课程（包括对应的签到状态）")
    public Result<List<Map<String, Object>>> todayCourse() {
        Long userId = tokenContextHolder.requireCurrentUserId();

        // 获取今天是周几
        java.time.DayOfWeek dayOfWeek = LocalDate.now().getDayOfWeek();
        // 将英文的周几转成中文
        String weekDay = switch (dayOfWeek) {
            case MONDAY -> "周一";
            case TUESDAY -> "周二";
            case WEDNESDAY -> "周三";
            case THURSDAY -> "周四";
            case FRIDAY -> "周五";
            case SATURDAY -> "周六";
            case SUNDAY -> "周日";
            default -> null;
        };

        // 如果周几转换失败 返回前端空列表
        if (weekDay == null) {
            return Result.success(List.of());
        }

        // 根据今天是周几 查询今天对应课程
        // 返回的数据是一个什么类型？ 整数 小数 字符串 某个实体 列表 列表嵌套某个实体
        // 一个课程数据从数据库查询的 就是一个课程实体
        // 多个课程 列表嵌套 课程实体
        List<Course> courses = courseService.lambdaQuery()
                // 查询状态正常的课程
                .eq(Course::getStatus, 1)
        // 查询今天的课程 (周二)
            .eq(Course::getWeekDay, weekDay)
                // 按上课时间正序排列
                .orderByAsc(Course::getStartTime)
                // 返回列表
                .list();

        // 假设今天没课 返回空列表
        if (courses.isEmpty()) {
            return Result.success(List.of());
        }

        // 获取今天的日期 (只包括年月日)
        LocalDate today = LocalDate.now();
        // 获取现在的时间 (只包括时分秒)
        LocalTime now = LocalTime.now();

        // 查询当前用户今天的课程签到状态
        List<ClassCheckin> todayCheckins = classCheckinService.lambdaQuery()
                // 查询当前用户的
                .eq(ClassCheckin::getUserId, userId)
                // 查询今天的
                .eq(ClassCheckin::getDate, today)
                .list();

        // 将签到记录转成 Map key 是课程id value 是签到记录
        Map<Long, ClassCheckin> checkinMap = todayCheckins.stream().collect(
                Collectors.toMap(
                        ClassCheckin::getCourseId,
                        c -> c, (ClassCheckin a, ClassCheckin b) -> a));

        // 存储 自动生成旷课记录列表
        List<ClassCheckin> absentRecords = new ArrayList<>();
        // 循环今天的课程 一条条去比对
        for (Course course : courses) {

            // 当前课程在 签到记录里面不存在 = 学生没有签到
            if (!checkinMap.containsKey(course.getId())) {
                // 提取当前课程结束的时间
                LocalTime end = LocalTime.parse(course.getEndTime());
                // 当前时间超过课程结束时间 (这门课已经下课)
                if (now.isAfter(end)) {
                    // 将 学生 这门课的签到记录标记为旷课
                    ClassCheckin absent = new ClassCheckin();
                    absent.setUserId(userId);
                    absent.setCourseId(course.getId());
                    absent.setDate(today);
                    // 记录标记旷课的时间
                    absent.setCheckinTime(LocalDateTime.now());
                    // 签到方式 auto (系统自动签到)
                    absent.setMethod("auto");
                    // 签到状态 absent = 旷课
                    absent.setStatus("absent");
                    // 加入 旷课记录里面 后面统一操作
                    absentRecords.add(absent);
                }
            }
        }

        if(! absentRecords.isEmpty())
        {
//            批量插入多条旷课签到记录
            classCheckinService.saveBatch(absentRecords);
            //将旷课记录 夜附加到checkMap 里面 返回签到
            for(ClassCheckin a : absentRecords){
                checkinMap.put(a.getCourseId(),a);
            }
        }
        //遍历所有课程 将课程信息和签到情况进行拼接
        List<Map<String,Object>> result = courses.stream().map(course -> {
            Map<String,Object> item = new HashMap<>();
            item.put("id", course.getId());
            item.put("name", course.getName());
            item.put("teacher", course.getTeacher());
            item.put("location", course.getLocation());
            item.put("startTime", course.getStartTime());
            item.put("endTime", course.getEndTime());
            item.put("section", course.getSection());
            item.put("weekDay", course.getWeekDay());

            // 根据课程id查询签到记录
            ClassCheckin checkin = checkinMap.get(course.getId());
            if(checkin != null){
                item.put("checkinStatus",checkin.getStatus());
                item.put("checkinTime",checkin.getCheckinTime() != null ? checkin.getCheckinTime().toString():null);

            }else {
                LocalTime start = LocalTime.parse(course.getStartTime());
                // 解析课程结束时间
                LocalTime end = LocalTime.parse(course.getEndTime());
                // 签到窗口提前十分钟开启
                LocalTime windowOpen = start.minusMinutes(10);
                // 课程开始时间在十分钟之后
                if(now.isBefore(windowOpen)){
                    // 不让用户签到
                    item.put("checkinStatus", "waiting");
                } else if (now.isAfter(end)) {
                    // 当前时间 晚于下课时间（已经下课，用户还没签到）
                    // 标记为旷课状态
                    item.put("checkinStatus", "absent");
                } else {
                    // 处于 签到时间 可以签到
                    item.put("checkinStatus", "ready");
                }
                    // 暂无签到时间
                item.put("checkinTime", null);

                item.put("checkinTime", null);
            }
            return item;
        }).collect(Collectors.toList());

        return Result.success(result);
    }

    @GetMapping("/history")
    @Operation(summary = "获取历史打卡列表")
    public Result<Page<Map<String, Object>>> history(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Long userId = tokenContextHolder.requireCurrentUserId();

        // 1. 分页查询签到记录（按日期+时间倒序 时间新的在前）
        Page<ClassCheckin> checkinPage = new Page<>(page, size);
        LambdaQueryWrapper<ClassCheckin> wrapper = new LambdaQueryWrapper<ClassCheckin>()
                .eq(ClassCheckin::getUserId, userId)
                .orderByDesc(ClassCheckin::getDate)
                .orderByDesc(ClassCheckin::getCheckinTime);

        Page<ClassCheckin> pageResult = classCheckinService.page(checkinPage, wrapper);

        // 2. 如果没有数据，直接返回空结果
        if (pageResult.getRecords().isEmpty()) {
            return Result.success(new Page<>(page, size));
        }

        // 3. 提取所有 courseId，批量查询课程信息
        List<Long> courseIds = pageResult.getRecords().stream()
                .map(ClassCheckin::getCourseId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, Course> courseMap = courseIds.isEmpty() ?
                Collections.emptyMap() :
                courseService.lambdaQuery()
                        .in(Course::getId, courseIds)
                        .list()
                        .stream()
                        .collect(Collectors.toMap(Course::getId, c -> c));

        // 4. 组装返回数据
        List<Map<String, Object>> records = pageResult.getRecords().stream()
                .map(checkin -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    Course course = courseMap.get(checkin.getCourseId());

                    // 课程信息
                    item.put("courseName", course != null ? course.getName() : "已删除课程");
                    item.put("startTime", course != null ? course.getStartTime() : null);
                    item.put("endTime", course != null ? course.getEndTime() : null);
                    item.put("teacher", course != null ? course.getTeacher() : null);
                    item.put("location", course != null ? course.getLocation() : null);

                    // 签到信息
                    item.put("checkinTime", checkin.getCheckinTime() != null ?
                            checkin.getCheckinTime().format(DateTimeFormatter.ofPattern("HH:mm:ss")) : null);
                    item.put("status", checkin.getStatus());  // present/absent/late
                    item.put("method", checkin.getMethod());  // location/qrcode/auto
                    item.put("date", checkin.getDate().toString());

                    // 计算显示状态（中文友好）
                    String statusText = switch (checkin.getStatus()) {
                        case "present" -> "已签到";
                        case "late" -> "迟到";
                        case "absent" -> "旷课";
                        default -> checkin.getStatus();
                    };
                    item.put("statusText", statusText);

                    return item;
                })
                .collect(Collectors.toList());

        // 5. 构建新的分页对象
        Page<Map<String, Object>> resultPage = new Page<>(page, size);
        resultPage.setTotal(pageResult.getTotal());
        resultPage.setRecords(records);

        return Result.success(resultPage);
    }

    @PostMapping("/checkin")
    @Operation(summary = "学生上课签到")
    public Result<ClassCheckin> checkin(
            @RequestBody ClassCheckinRequest request
    ) {
        Long userId = tokenContextHolder.requireCurrentUserId();

        // 人脸验证通过判断
        if (request.getFaceVerified() == null || !request.getFaceVerified()) {
            return Result.error("请先完成人脸验证");
        }

        // 课程参数是否正常
        if (request.getCourseId() == null) {
            return Result.error("课程id不能为空");
        }

        // 校验课程信息是否存在
        Course course = courseService.getById(request.getCourseId());
        if (course == null) {
            return Result.error("课程不存在");
        }

        // 计算签到时间窗口
        LocalTime startTime = LocalTime.parse(course.getStartTime());
        LocalTime endTime = LocalTime.parse(course.getEndTime());
        LocalTime now = LocalTime.now();
        // 提前10分钟开放签到窗口
        LocalTime windowOpen = startTime.minusMinutes(10);

        // 检查一下是否早于签到窗口
        if (now.isBefore(windowOpen)) {
            long minutesUntilOpen = Duration.between(now, windowOpen).toMinutes();
            return Result.error("签到尚未开始，离签到时间还有" + minutesUntilOpen + "分钟");
        }

        if (now.isAfter(endTime)) {
            return Result.error("课程已结束，不能签到");
        }

        // 下面都是可签到情况
        // 获取日期 只包括 年月日
        LocalDate today = LocalDate.now();

        // 查询当前用户上课签到情况
        ClassCheckin existing = classCheckinService.lambdaQuery()
                // 当前用户
                .eq(ClassCheckin::getUserId, userId)
                // 当前课程
                .eq(ClassCheckin::getCourseId, request.getCourseId())
                // 当天
                .eq(ClassCheckin::getDate, today)
                .one();

        // 确定签到状态
        // 签到时间晚于上课时间 迟到 早于 正常
        String status = now.isBefore(startTime) ? "present" : "late";

        if (existing != null) {
            // 今天当前用户当前课程已经签到 更新签到信息
            existing.setCheckinTime(LocalDateTime.now());
            existing.setMethod(request.getMethod());
            existing.setLocationLat(request.getLocationLat());
            existing.setLocationLng(request.getLocationLng());
            existing.setLocationAddr(request.getLocationAddr());
            existing.setDynamicCode(request.getDynamicCode());
            existing.setStatus(status);
            classCheckinService.updateById(existing);
            featureDailyService.updateOnClassCheckin(userId, today, status);
            return Result.success("重新签到成功", existing);
        }

        // 今天当前用户当前课程没有签到 增加签到记录
        ClassCheckin checkin = new ClassCheckin();
        checkin.setCheckinTime(LocalDateTime.now());
        checkin.setMethod(request.getMethod());
        checkin.setLocationLat(request.getLocationLat());
        checkin.setLocationLng(request.getLocationLng());
        checkin.setLocationAddr(request.getLocationAddr());
        checkin.setDynamicCode(request.getDynamicCode());
        checkin.setStatus(status);
        checkin.setUserId(userId);
        checkin.setCourseId(request.getCourseId());
        checkin.setDate(today);

        // 对数据库表进行插入操作
        classCheckinService.save(checkin);

        // 更新特征数据
        featureDailyService.updateOnClassCheckin(userId, today, status);

        // 打卡统计表进行更新
        // 打卡统计表有没有当前用户的记录
        DormCheckinStats stats = dormcheckinStatsService.lambdaQuery()
                .eq(DormCheckinStats::getUserId, userId).one();

        // 打卡统计表有当前用户的记录 课程打卡数据+1
        if (stats != null) {
            stats.setClassTotal(stats.getClassTotal() + 1);
            dormcheckinStatsService.updateById(stats);
        } else {
            // 打卡统计表没有当前用户的记录 初始化 课程打卡数据为1
            DormCheckinStats newStats = new DormCheckinStats();
            newStats.setUserId(userId);
            newStats.setDormTotal(0);
            newStats.setClassTotal(1);
            newStats.setInternTotal(0);
            newStats.setStreakDays(0);
            newStats.setTotalPoints(0);
            dormcheckinStatsService.save(newStats);
        }

        return Result.success("签到成功", checkin);
    }

    @GetMapping("/schedule")
    @Operation(summary = "获取完整课程表（整周）")
    public Result<List<Course>> schedule() {
        // 查询所有启用的课程，按星期几、上课时间排序
        List<Course> list = courseService.lambdaQuery()
                .eq(Course::getStatus, 1)
                .orderByAsc(Course::getWeekDay)
                .orderByAsc(Course::getStartTime)
                .list();
        return Result.success(list);
    }

}
