package com.healthdiet.recommend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NutritionSnapshot {
    private int calories;
    private double proteinG;
    private double fatG;
    private double carbG;
    private double fiberG;
}
