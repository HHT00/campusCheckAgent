package com.educheck.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.educheck.entity.UserFace;

public interface UserFaceService extends IService<UserFace> {

    public UserFace getUserFace(Long Userid);
    public boolean isFaceRegistered(Long userId);
    public String getUserFaceBase64(Long userId);

}
