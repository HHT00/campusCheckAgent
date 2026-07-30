package com.educheck.controller;

import com.educheck.common.Result;
import com.educheck.common.TokenContextHolder;
import com.educheck.config.BaiduCloudConfig;
import com.educheck.dto.FaceRegisterRequest;
import com.educheck.dto.FaceVerifyRequest;
import com.educheck.entity.UserFace;
import com.educheck.service.BaiduCloudFaceService;
import com.educheck.service.UserFaceService;
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
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

//日志注解
@Slf4j
//接口返回的数据格式为json
@RestController
//定义当前控制的处理的接口
@RequestMapping("/api/face")

//创建service对象
//给所有的final修饰的成员变量 创建对应的对象
@RequiredArgsConstructor

//
@Tag(name="人脸管理" , description = "人脸录入与描述")
public class FaceController {

    private final UserFaceService userFaceService;
    private final TokenContextHolder tokenContextHolder;

    @Value("${face.upload-dir:uploads/face}")
    private String faceUploadDir;

//    定义百度service
    private final BaiduCloudFaceService baiduCloudFaceService;

//    人脸录入接口
    @PostMapping("/register")
    @Operation(summary = "人脸录入")
    public Result<Map<String,Object>> register(
            @RequestBody FaceRegisterRequest request
    ){
        Long userId = tokenContextHolder.requireCurrentUserId();
        if(request.getImage() == null || request.getImage().isEmpty()){
            return Result.error("人脸识别不能为空");
        }
        try{
//            确保文件路径存在
            Path uploadDir = Paths.get(faceUploadDir);
//            如果文件路径不存在，创建这个目录
            if( !Files.exists(uploadDir)){
                Files.createFile(uploadDir);
            }
//            将人脸图片保存到服务器
            String format = request.getFormat()!= null ? request.getFormat(): "jpg";
//            拼接文件名 文件名字 + 后缀
//            uuid 唯一字符串 时间戳加随机数
            String fileName = "face_" + userId+"_"+ UUID.randomUUID().toString().substring(0,8)+"."+format;

//            得到文件完整路径
            Path filePath = uploadDir.resolve(fileName);
            byte[] imagesBytes = Base64.getDecoder().decode(request.getImage());
//            将图片写进服务器 硬盘里面
            Files.write(filePath,imagesBytes);

//            用户是否录入过人脸信息，如果录入过就删除旧路径，
//            设置新路径，版本加一;如果没有录入过，就插入记录，版本号为一
            UserFace existing = userFaceService.getUserFace(userId);
            if(existing != null){

                try{
//                    拿到旧图片路径
                    Path oldPath = Paths.get(faceUploadDir,existing.getFaceUrl());
                    Files.deleteIfExists(oldPath);
                }catch(IOException e){
                    log.warn("删除旧图片失败："+e.getMessage());
                }
                existing.setFaceUrl(fileName);
                existing.setVersion(existing.getVersion() != null ?  existing.getVersion()+1 : 2);
                existing.setRegistered(1);
                existing.setUpdatedAt(LocalDateTime.now());
                userFaceService.updateById(existing);
                log.info("人脸更新成功");
            }else {
//                执行插入操作
                UserFace userFace = new UserFace();
                userFace.setUserId(userId.intValue());
                userFace.setFaceUrl(fileName);
                userFace.setRegistered(1);
                userFace.setVersion(1);
                userFaceService.save(userFace);
                log.info("人脸信息更新成功");
            }
        Map<String,Object> data = new HashMap<>();
        data.put("registered",true);
        data.put("message",existing != null ? "人脸更新成功":"人脸录入成功");
        return  Result.success(data);

        }catch (IOException e){
        log.error("保存人脸图片失败：userid:",userId,e);
        Result.error("人脸图片保存失败");
        }
        return null;
    }

    @GetMapping("/status")
    @Operation(summary = "获得人脸状态")
    public Result<Map<String,Object>> status(){


//        获得用户id
        Long userId = tokenContextHolder.requireCurrentUserId();
//        通过用户id取获取用户人脸验证信息
        UserFace userFace = userFaceService.getUserFace(userId);
//        创建一个Map对象用来返回结果
        Map<String,Object> data = new HashMap<>();
        data.put("registered",userFace != null);

        if(userFace != null){
            data.put("version",userFace.getVersion());
            data.put("registeredAt",userFace.getCreatedAt());
            data.put("updatedAt",userFace.getUpdatedAt());

        }
        return Result.success(data);
    }

    @PostMapping("/verify")
    @Operation(summary = "人脸认证")
    public Result<Map<String,Object>> verify(
            @RequestBody FaceVerifyRequest request
            ){
        Long userId = tokenContextHolder.getCurrentUserId();
        if(request.getImage()==null || request.getImage().isEmpty()){
            return Result.error("图片不能为空");
        }
        if(!userFaceService.isFaceRegistered(userId)){
            return Result.error("请先录入图片");

        }
//        获取已录入的人脸图片 base64数据
        String rBase64 = userFaceService.getUserFaceBase64(userId);
        if(rBase64 == null){
            return Result.error("已经录入的人脸图片不存在");

        }
//        调用百度Ai进行人脸比对
        BaiduCloudFaceService.FaceMatchResult result = baiduCloudFaceService.compare(request.getImage(),rBase64);
        if( !result.success()){
            return  Result.error(result.errorMsg());

        }
//        结果封装
        Map<String ,Object> data = new HashMap<>();

//        封装结果评分
        data.put("score",result.score());

//        封装人脸对比是否通过，大于80分就通过
        data.put("passed",result.passed());
//        相似度大于80即通过
        data.put("threshold",80);
        if(result.passed()){
            return Result.success("验证通过",data);

        }else{
            return Result.error(400,"人脸检测失败，相似度不足");
        }

    }
}
