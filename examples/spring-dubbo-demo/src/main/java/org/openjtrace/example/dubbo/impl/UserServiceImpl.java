package org.openjtrace.example.dubbo.impl;

import org.openjtrace.example.dubbo.UserService;
import org.openjtrace.example.mybatis.UserMapper;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;

@DubboService
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Override
    public int getUser(int id) {
        return userMapper.selectById(id);
    }
}
