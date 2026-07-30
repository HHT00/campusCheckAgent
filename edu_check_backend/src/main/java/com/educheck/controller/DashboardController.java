package com.educheck.controller;


import com.educheck.common.Result;
import com.educheck.common.TokenContextHolder;
import com.educheck.entity.DormCheckinStats;
import com.educheck.entity.User;
import com.educheck.service.DormCheckinStatsService;
import com.educheck.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;


//接口返回的数据格式为json
@RestController
//定义当前控制的处理的接口
@RequestMapping("/api/dashboard")

//创建service对象
//给所有的final修饰的成员变量 创建对应的对象
@RequiredArgsConstructor

//
@Tag(name="首页仪表盘" , description = "首页数据总览接口")
public class DashboardController {

    private final UserService userService;
    private final TokenContextHolder tokenContextHolder;
    private final DormCheckinStatsService checkinStatsService;

// 指定请求方式为get
//  这个方法处理的完整接口路径为 /api/Dashboard/banners
    @GetMapping("/banners")
//获得文档提示
    @Operation(summary = "获取轮播图列表并修改")
    public Result<String[]> banners(){
        return Result.success( new String[] {"https://ts1.tc.mm.bing.net/th?id=ORMS.b3c73d45350c086a81905c8148aad960&pid=Wdp&w=612&h=304&qlt=90&c=1&rs=1&dpr=1.5&p=0"});
    }

    // 指定请求方式为get
//  这个方法处理的完整接口路径为 /api/Dashboard/overview
    @GetMapping("/overview")
//获得文档提示
    @Operation(summary = "获取首页描述信息")
    public Result<Map<String, Object>> overview(){
//        获取用户ID
        Long userId = tokenContextHolder.requireCurrentUserId();
//        根据用户ID，从用户列表里面获取用户信息
        User user = userService.getById(userId);

        Map<String,Object> data = new HashMap<>();

        if(user != null){
            Map<String , Object> userInfo = new HashMap();
            userInfo.put("name",user.getName());
            userInfo.put("college",user.getCollege());
            userInfo.put("studentId",user.getStudentId());

//            用户头像url
//            String Avatarurl= "https://img-s.msn.cn/tenant/amp/entityid/AA28dRG9.img?w=268&h=140&q=60&m=6&f=jpg&x=558&y=295&u=t";
//            user.setAvatar(Avatarurl);

            userInfo.put("avatar",user.getAvatar());

            data.put("userInfo",userInfo);
        }
//        从 checkin_stats 表读取真实统计数据
        DormCheckinStats stats = checkinStatsService.lambdaQuery()
                .eq(DormCheckinStats::getUserId, userId)
                .one();
        Map<String, Object> statsMap = new HashMap<>();
        if (stats != null) {
            statsMap.put("dormTotal", stats.getDormTotal() != null ? stats.getDormTotal() : 0);
            statsMap.put("classTotal", stats.getClassTotal() != null ? stats.getClassTotal() : 0);
            statsMap.put("internTotal", stats.getInternTotal() != null ? stats.getInternTotal() : 0);
            statsMap.put("streakDays", stats.getStreakDays() != null ? stats.getStreakDays() : 0);
        } else {
            statsMap.put("dormTotal", 0);
            statsMap.put("classTotal", 0);
            statsMap.put("internTotal", 0);
            statsMap.put("streakDays", 0);
        }
        data.put("stats",statsMap);
        return  Result.success(data);
    }

}
