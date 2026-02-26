package com.healthdiet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.healthdiet.entity.Food;
import org.apache.ibatis.annotations.Mapper;

@Mapper // 一定要加这个注解，不然报错找不到
public interface FoodMapper extends BaseMapper<Food> {
}
