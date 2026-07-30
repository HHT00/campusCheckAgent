package com.educheck.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.educheck.entity.Notification;
import com.educheck.mapper.NotificationMapper;
import com.educheck.service.NotificationService;
import org.springframework.stereotype.Service;

@Service
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification> implements NotificationService {
}
