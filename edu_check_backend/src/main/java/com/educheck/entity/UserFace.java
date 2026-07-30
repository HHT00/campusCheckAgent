package com.educheck.entity;


import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

//自动生成get与set方法
@Data

//指定关联的数据库表名
@TableName("user_face")
public class UserFace {

//    自增主键
    @TableId(type= IdType.AUTO)
    private Integer id;

//    用户Id
    private Integer userId;

//   人脸识别图片保存路径
    private String faceUrl;

//    百度云返回值
    private String faceToken;

//    是否注册
    private Integer registered;

//    注册次数
    private Integer version;

    @TableField(fill= FieldFill.INSERT_UPDATE)
//  创建时间
    private LocalDateTime createdAt;


//   更新时间
@TableField(fill= FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

}
