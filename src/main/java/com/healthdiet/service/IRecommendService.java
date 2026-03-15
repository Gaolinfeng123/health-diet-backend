package com.healthdiet.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.healthdiet.entity.Recommendation;

public interface IRecommendService extends IService<Recommendation> {

    Recommendation getTodayRecommend(Long userId);

    Recommendation refreshTodayRecommend(Long userId);
}
