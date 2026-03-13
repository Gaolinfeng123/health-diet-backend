package com.healthdiet.recommend.model;

import com.healthdiet.entity.Food;
import lombok.Data;

@Data
public class MealSolution {
    private Food staple;
    private Food protein;
    private Food veggie;

    private int stapleWeight;
    private int proteinWeight;
    private int veggieWeight;

    private int actualCalories;
    private double proteinG;
    private double fatG;
    private double carbG;

    private double score;
}
