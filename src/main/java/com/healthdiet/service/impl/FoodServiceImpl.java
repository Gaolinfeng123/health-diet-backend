package com.healthdiet.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.healthdiet.entity.Food;
import com.healthdiet.mapper.FoodMapper;
import com.healthdiet.service.IFoodService;
import org.springframework.stereotype.Service;

@Service // 告诉 Spring 这是一个业务逻辑类
public class FoodServiceImpl extends ServiceImpl<FoodMapper, Food> implements IFoodService {
}
