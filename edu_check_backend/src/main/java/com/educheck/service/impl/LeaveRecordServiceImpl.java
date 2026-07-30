package com.educheck.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.educheck.entity.LeaveRecord;
import com.educheck.mapper.LeaveRecordMapper;
import com.educheck.service.LeaveRecordService;
import org.springframework.stereotype.Service;

/**
 * 请假记录 Service 实现
 */
@Service
public class LeaveRecordServiceImpl
        extends ServiceImpl<LeaveRecordMapper, LeaveRecord>
        implements LeaveRecordService {
}
