package com.educheck.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.educheck.entity.InternCheckin;
import com.educheck.mapper.InternCheckinMapper;
import com.educheck.service.InternCheckinService;
import org.springframework.stereotype.Service;

@Service
public class InternCheckinServiceImpl
        extends ServiceImpl<InternCheckinMapper, InternCheckin>
        implements InternCheckinService {
}
