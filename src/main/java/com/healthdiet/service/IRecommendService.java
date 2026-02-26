package com.healthdiet.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.healthdiet.entity.Recommendation; // 必须导入实体类

// 【关键点】这里必须是 <Recommendation>
// 绝对不能写成 IService<Double>
public interface IRecommendService extends IService<Recommendation> {

    // 生成并获取今日推荐
    Recommendation getTodayRecommend(Long userId);
}