package com.educheck.controller;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.educheck.common.Result;
import com.educheck.common.TokenContextHolder;
import com.educheck.dto.DormCheckinRequest;
import com.educheck.entity.DormCheckin;
import com.educheck.entity.DormCheckinStats;
import com.educheck.service.DormCheckinService;
import com.educheck.service.DormCheckinStatsService;
import com.educheck.service.feature.FeatureDailyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.annotations.Results;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

//日志
@Slf4j

//接口返回的数据格式为json
@RestController

//定义当前控制的处理的接口
@RequestMapping("/api/dorm")

//创建service对象
//给所有的final修饰的成员变量 创建对应的对象
@RequiredArgsConstructor

//文档提示
@Tag(name="查寝打卡" , description = "查侵打卡接口")
public class DormCheckinController {

//    创建对应的对象 和 token 上下文
    private final DormCheckinService dormCheckinService;
    private final DormCheckinStatsService dormCheckinStatsService;
    private final TokenContextHolder tokenContextHolder;
    private final FeatureDailyService featureDailyService;

    @GetMapping("/today-status")
    @Operation(summary = "获取今天打卡状态")
    public Result<DormCheckin> todayStatus(){
        //获取用户id
        Long userId = tokenContextHolder.requireCurrentUserId();
//      根据条件查询 单条打卡记录
        DormCheckin checkin = dormCheckinService.lambdaQuery()
                .eq(DormCheckin::getUserId,userId)
                .eq(DormCheckin::getDate, LocalDate.now())
                .one();
        return Result.success(checkin);

    }

    @GetMapping("/history")
    @Operation(summary = "获取历史打卡列表")
    public Result<Page<DormCheckin>> history(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ){
        Long userId = tokenContextHolder.requireCurrentUserId();
        LambdaQueryWrapper<DormCheckin> wrapper = new LambdaQueryWrapper<DormCheckin>()
                .eq(DormCheckin::getUserId,userId)
//                按照 日期，时间 倒序排序
                .orderByDesc(DormCheckin::getDate,DormCheckin::getCheckinTime);
        return Result.success(dormCheckinService.page(new Page<>(page,size),wrapper));
    }


    @PostMapping("/checkin")
    @Operation(summary = "查寝打卡")
    public Result<DormCheckin> checkin(
            @RequestBody DormCheckinRequest request
    ){

//      获得用户id
        Long userId = tokenContextHolder.getCurrentUserId();

//      人脸验证检查 通过getFaceVerified()方法检查是否有人脸认证记录，没有则返回报错
        if(request.getFaceVerified() == null || !request.getFaceVerified()){
            return Result.error("请先完成人脸识别验证");
        }
//      当前打卡时间
        LocalTime now = LocalTime.now();
        LocalTime curfew = LocalTime.of(22,0);

//        打卡状态设置 在22：0：0之后的属于晚归
        String status = now.isAfter(curfew)? "late":"normal";
//        查询dorm_checkin 表今天有没有打卡记录
        DormCheckin existing = dormCheckinService.lambdaQuery()
                .eq(DormCheckin::getUserId,userId)
                .eq(DormCheckin::getDate,LocalDate.now())
                .one();
        if(existing != null){
            existing.setCheckinTime(LocalDateTime.now());
            existing.setLocationLat(request.getLocationLat());
            existing.setLocationLng(request.getLocationLng());
            existing.setLocationAddr(request.getLocationAddr());
            existing.setBuilding(request.getBuilding());
            existing.setRoom(request.getRoom());
//            根据前端参数 是否在宿舍 存入 1 0
            existing.setInDormArea(request.getInDormArea() != null && request.getInDormArea()?1:0);
//            已经 经过人脸验证
            existing.setFaceVerified(1);

//            如果有人脸图片 将人脸图片 存入查寝打卡记录里面
            if(request.getFaceVerified() != null && !request.getFaceImage().isEmpty()  ){
                existing.setFaceImageUrl(request.getFaceImage());
            }
            existing.setStatus(status);
//            数据库对记录进行更新
            dormCheckinService.updateById(existing);
            featureDailyService.updateOnDormCheckin(userId, LocalDate.now(), now, existing.getBuilding());
            return Result.success("更新查询打卡成功",existing);

        }

//      今天第一次打卡 创建对应的查寝打卡实体 把每个信息存入
        DormCheckin checkin = new DormCheckin();
        checkin.setUserId(userId);
        checkin.setDate(LocalDate.now());
        checkin.setCheckinTime(LocalDateTime.now());
        checkin.setLocationLat(request.getLocationLat());
        checkin.setLocationLng(request.getLocationLng());
        checkin.setLocationAddr(request.getLocationAddr());
        checkin.setBuilding(request.getBuilding());
        checkin.setRoom(request.getRoom());
        checkin.setInDormArea(request.getInDormArea() != null && request.getInDormArea() ?1:0);
        checkin.setFaceVerified(1);
        if(request.getFaceImage() != null && !request.getFaceImage().isEmpty()){
            checkin.setFaceImageUrl(request.getFaceImage());
        }
        checkin.setStatus(status);
        dormCheckinService.save(checkin);
        featureDailyService.updateOnDormCheckin(userId, LocalDate.now(), now, request.getBuilding());
        DormCheckinStats stats = dormCheckinStatsService.lambdaQuery()
                .eq(DormCheckinStats::getUserId,userId)
                .one();
        if(stats != null){
//            查寝打卡次数加一
            stats.setDormTotal(stats.getDormTotal()+1);
            dormCheckinStatsService.updateById(stats);
        }else {
//          该用户今天没有打卡记录 新增记录
            DormCheckinStats newStats = new DormCheckinStats();
            newStats.setUserId(userId);
            newStats.setDormTotal(1);
            newStats.setClassTotal(0);
            newStats.setInternTotal(0);
            newStats.setStreakDays(0);
            newStats.setTotalPoints(0);
            dormCheckinStatsService.save(newStats);
        }
        return Result.success("打卡成功",checkin);

    }

}
