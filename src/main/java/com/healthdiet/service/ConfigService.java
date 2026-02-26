package com.healthdiet.service;

import org.springframework.stereotype.Service;

@Service
public class ConfigService {

    /**
     * 获取配置的简化版实现
     * 如果你以后想做数据库配置，再改写这个方法去查表
     */
    public double getDouble(String key, double defaultValue) {
        // 目前直接返回默认值，保证算法能跑通
        return defaultValue;
    }
}