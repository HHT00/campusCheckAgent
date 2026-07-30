package com.educheck.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.educheck.entity.UnansweredLog;
import com.educheck.mapper.UnansweredLogMapper;
import com.educheck.service.UnansweredLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


@Slf4j
@Service
public class UnansweredLogServiceImpl
        extends ServiceImpl<UnansweredLogMapper, UnansweredLog>
        implements UnansweredLogService {
}
