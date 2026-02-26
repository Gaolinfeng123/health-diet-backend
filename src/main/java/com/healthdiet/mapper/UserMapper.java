package com.healthdiet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.healthdiet.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
