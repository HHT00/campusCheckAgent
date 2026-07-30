package com.educheck.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.educheck.entity.DormCheckinStats;
import com.educheck.mapper.DormCheckinStatsMapper;
import com.educheck.service.DormCheckinStatsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


@Slf4j
@Service
public class DormCheckinStatsServiceImpl
        extends ServiceImpl<DormCheckinStatsMapper, DormCheckinStats>
        implements DormCheckinStatsService {

}
