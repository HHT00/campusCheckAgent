package com.educheck.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;


//自动生成get set 方法
@Data

//指定关联的数据库库表名
@TableName("dorm_checkin")
public class DormCheckin {


    //    自增主键
    @TableId(type= IdType.AUTO)
    private Long id;

//    用户id
    private Long userId;

//    打卡日期
    private LocalDate date ;

//    打卡时间
    private LocalDateTime checkinTime;

//   纬度
    private BigDecimal locationLat;
//    经度
    private BigDecimal locationLng;

//    定位位置
    private String locationAddr;
//  宿舍楼
    private String building;

//    宿舍号
    private String room;

//    是否在宿舍区域
    private Integer inDormArea;

//    人脸是否通过认证
    private Integer faceVerified;

//    人脸图片url
    private String faceImageUrl;

//    状态
    private String status;

//    创建时间
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime createdAt;


}
