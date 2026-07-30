package com.educheck.service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.educheck.entity.ClassCheckin;
import com.educheck.mapper.ClassCheckinMapper;
import com.educheck.service.ClassCheckinService;
import org.springframework.stereotype.Service;

@Service
public class ClassCheckinServiceImpl extends ServiceImpl<ClassCheckinMapper, ClassCheckin> implements ClassCheckinService {
}
