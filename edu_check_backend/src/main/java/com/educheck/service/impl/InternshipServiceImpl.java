package com.educheck.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.educheck.entity.Internship;
import com.educheck.mapper.InternshipMapper;
import com.educheck.service.InternshipService;
import org.springframework.stereotype.Service;

@Service
public class InternshipServiceImpl
        extends ServiceImpl<InternshipMapper, Internship>
        implements InternshipService {
}
