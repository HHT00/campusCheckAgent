package com.educheck.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.educheck.entity.AlertEvent;
import com.educheck.mapper.AlertEventMapper;
import com.educheck.service.AlertEventService;
import org.springframework.stereotype.Service;

@Service
public class AlertEventServiceImpl extends ServiceImpl<AlertEventMapper, AlertEvent> implements AlertEventService {
}
