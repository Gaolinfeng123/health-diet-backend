package com.healthdiet.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.healthdiet.entity.dto.LoginDTO;
import com.healthdiet.entity.dto.RegisterDTO;
import com.healthdiet.entity.User;

public interface IUserService extends IService<User> {
    // 参数改为 RegisterDTO
    void register(RegisterDTO dto);

    // 参数改为 LoginDTO
    String login(LoginDTO dto);
}