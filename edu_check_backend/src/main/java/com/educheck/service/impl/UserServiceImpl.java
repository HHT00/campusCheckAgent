package com.educheck.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.educheck.entity.User;
import com.educheck.mapper.UserMapper;
import com.educheck.service.UserService;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl
        extends ServiceImpl<UserMapper, User>
        implements UserService {


}
