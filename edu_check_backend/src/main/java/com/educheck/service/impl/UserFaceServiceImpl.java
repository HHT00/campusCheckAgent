package com.educheck.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.educheck.entity.UserFace;
import com.educheck.mapper.UserFaceMapper;
import com.educheck.service.UserFaceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

@Slf4j
@Service
public class UserFaceServiceImpl
        extends ServiceImpl<UserFaceMapper,UserFace>
        implements UserFaceService {

    @Value("${face.upload-dir:uploads/face}")
    private String faceUploadDir;


    @Override
    public UserFace getUserFace(Long Userid) {
        try {
            return lambdaQuery().eq(UserFace::getUserId, Userid)
                    .eq(UserFace::getRegistered, 1)
                    .one();
        } catch (DataAccessException e) {
            log.warn("人脸录入失败:{}" + e.getMessage());
            return null;
        }
    }

    @Override
    public boolean isFaceRegistered(Long userId) {
        try{
            return lambdaQuery()
                    .eq(UserFace::getUserId,userId)
                    .eq(UserFace::getRegistered,1)
                    .count()>0;
        }catch (Exception e ){
            log.warn("检查人脸失败{}",e.getMessage());
            return false;
        }
    }

    @Override
    public String getUserFaceBase64(Long userId) {
//        根据用户id，得到用户信息
        UserFace userFace = getUserFace(userId);
        if(userFace == null || userFace.getFaceUrl() == null){
            return null;
        }
//        检查路径是否存在
        try{
            Path filePath = Paths.get(faceUploadDir,userFace.getFaceUrl());
            if(!Files.exists(filePath)){
                log.warn("not found {}",filePath);
                return null;
            }
//            返回图片字符串
            byte[] fileBytes = Files.readAllBytes(filePath);
            return Base64.getEncoder().encodeToString(fileBytes);
        } catch (Exception e) {
            log.warn("not found {}",userId,e.getMessage());
            return null;
        }

    }
}