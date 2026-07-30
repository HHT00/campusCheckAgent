package com.educheck.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.educheck.entity.DynamicCode;
import com.educheck.mapper.DynamicCodeMapper;
import com.educheck.service.DynamicCodeService;
import org.springframework.stereotype.Service;

@Service
public class DynamicCodeServiceImpl extends ServiceImpl<DynamicCodeMapper, DynamicCode> implements DynamicCodeService {
}
