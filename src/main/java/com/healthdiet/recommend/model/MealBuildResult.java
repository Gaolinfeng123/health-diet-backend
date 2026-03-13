package com.healthdiet.recommend.model;

import com.healthdiet.entity.vo.RecommendVO;
import lombok.Data;

@Data
public class MealBuildResult {
    private RecommendVO.Meal meal;
    private Long stapleId;
    private Long proteinId;
    private Long veggieId;
}