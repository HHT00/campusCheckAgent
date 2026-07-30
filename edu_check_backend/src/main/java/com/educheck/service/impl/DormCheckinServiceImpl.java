package com.educheck.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.educheck.entity.DormCheckin;
import com.educheck.mapper.DormCheckinMapper;
import com.educheck.service.DormCheckinService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


@Slf4j
@Service
public class DormCheckinServiceImpl
        extends ServiceImpl<DormCheckinMapper,DormCheckin>
        implements DormCheckinService {

    @Override
    public DormCheckin checkin(Long userId) {

        return null;
    }

    @Override
    public DormCheckin history(Long userId) {
        return null;
    }

    @Override
    public DormCheckin todayStatus(Long userId) {
        return null;
    }
}
