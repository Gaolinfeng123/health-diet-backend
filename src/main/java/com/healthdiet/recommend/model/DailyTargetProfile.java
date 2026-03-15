package com.healthdiet.recommend.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DailyTargetProfile {
    private int targetCalories;
    private double targetProteinG;
    private double targetFatG;
    private double targetCarbG;

    private double bmr;
    private double tdee;
    private double activityFactor;

    private int baseTargetCalories;
    private int historyCalorieAdjustment;

    private double weightedAverageCalories;
    private double weightedAverageProteinG;
    private double weightedAverageFatG;
    private double weightedAverageCarbG;
    private double weightedAverageFiberG;

    private final List<String> decisionTags = new ArrayList<>();

    public boolean hasTag(String tag) {
        return decisionTags.contains(tag);
    }

    public void addDecisionTag(String tag) {
        if (!decisionTags.contains(tag)) {
            decisionTags.add(tag);
        }
    }
}
