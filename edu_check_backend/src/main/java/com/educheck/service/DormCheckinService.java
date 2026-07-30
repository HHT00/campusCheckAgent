package com.educheck.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.educheck.entity.DormCheckin;

public interface DormCheckinService extends IService<DormCheckin> {

    public DormCheckin checkin(Long userId);
    public DormCheckin history(Long userId);
    public DormCheckin todayStatus(Long userId);
}
