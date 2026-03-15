package com.healthdiet.recommend.model;

import lombok.Data;

@Data
public class DayPlanCandidate {
    private MealSolution breakfast;
    private MealSolution lunch;
    private MealSolution dinner;
    private double score;
}
