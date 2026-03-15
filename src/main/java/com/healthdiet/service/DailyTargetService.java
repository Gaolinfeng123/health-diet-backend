package com.healthdiet.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.healthdiet.entity.DietRecord;
import com.healthdiet.entity.Food;
import com.healthdiet.entity.User;
import com.healthdiet.mapper.DietRecordMapper;
import com.healthdiet.mapper.FoodMapper;
import com.healthdiet.recommend.enums.ActivityLevel;
import com.healthdiet.recommend.enums.GoalType;
import com.healthdiet.recommend.model.DailyTargetProfile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DailyTargetService {

    @Autowired
    private DietRecordMapper dietRecordMapper;

    @Autowired
    private FoodMapper foodMapper;

    public DailyTargetProfile buildDailyTarget(User user, GoalType goalType, LocalDate date) {
        if (user == null) {
            throw new IllegalArgumentException("user cannot be null");
        }

        DailyTargetProfile profile = new DailyTargetProfile();
        ActivityLevel activityLevel = ActivityLevel.fromCode(user.getActivityLevel());
        double bmr = calculateBMR(user);
        double tdee = bmr * activityLevel.getFactor();

        profile.setBmr(bmr);
        profile.setTdee(tdee);
        profile.setActivityFactor(activityLevel.getFactor());
        profile.addDecisionTag("ACTIVITY_" + activityLevel.name());

        int baseTargetCalories = (int) Math.round(tdee) + goalCalorieAdjustment(goalType);
        if (baseTargetCalories < Math.round(bmr)) {
            baseTargetCalories = (int) Math.round(bmr);
        }
        profile.setBaseTargetCalories(baseTargetCalories);

        HistoryAverages history = analyzeHistory(user.getId(), date.minusDays(7), date.minusDays(1));
        profile.setWeightedAverageCalories(round1(history.weightedCalories));
        profile.setWeightedAverageProteinG(round1(history.weightedProtein));
        profile.setWeightedAverageFatG(round1(history.weightedFat));
        profile.setWeightedAverageCarbG(round1(history.weightedCarb));
        profile.setWeightedAverageFiberG(round1(history.weightedFiber));

        int historyCalorieAdjustment = calculateHistoryCalorieAdjustment(baseTargetCalories, history.weightedCalories);
        profile.setHistoryCalorieAdjustment(historyCalorieAdjustment);

        int finalTargetCalories = baseTargetCalories + historyCalorieAdjustment;
        if (finalTargetCalories < Math.round(bmr)) {
            finalTargetCalories = (int) Math.round(bmr);
        }
        profile.setTargetCalories(finalTargetCalories);

        double[] ratios = adjustedMacroRatios(goalType, baseTargetCalories, history, profile);
        profile.setTargetProteinG(round1(finalTargetCalories * ratios[0] / 4.0));
        profile.setTargetFatG(round1(finalTargetCalories * ratios[1] / 9.0));
        profile.setTargetCarbG(round1(finalTargetCalories * ratios[2] / 4.0));
        return profile;
    }

    private HistoryAverages analyzeHistory(Long userId, LocalDate start, LocalDate end) {
        HistoryAverages averages = new HistoryAverages();
        if (userId == null || start.isAfter(end)) {
            return averages;
        }

        QueryWrapper<DietRecord> query = new QueryWrapper<>();
        query.eq("user_id", userId).between("date", start, end);
        List<DietRecord> records = dietRecordMapper.selectList(query);
        if (records == null || records.isEmpty()) {
            return averages;
        }

        Set<Long> foodIds = records.stream().map(DietRecord::getFoodId).collect(Collectors.toSet());
        Map<Long, Food> foodMap = foodMapper.selectBatchIds(foodIds)
                .stream()
                .collect(Collectors.toMap(Food::getId, food -> food));

        Map<LocalDate, DailyTotals> dailyTotalsMap = new HashMap<>();
        for (DietRecord record : records) {
            Food food = foodMap.get(record.getFoodId());
            if (food == null) {
                continue;
            }

            DailyTotals totals = dailyTotalsMap.computeIfAbsent(record.getDate(), ignored -> new DailyTotals());
            double qty = safeInt(record.getQuantity());
            totals.calories += safe(food.getCalories()) * qty;
            totals.protein += safe(food.getProtein()) * qty;
            totals.fat += safe(food.getFat()) * qty;
            totals.carb += safe(food.getCarb()) * qty;
            totals.fiber += decimalValue(food.getFiber()) * qty;
        }

        if (dailyTotalsMap.isEmpty()) {
            return averages;
        }

        double totalWeight = 0.0;
        for (Map.Entry<LocalDate, DailyTotals> entry : dailyTotalsMap.entrySet()) {
            long daysAgo = end.toEpochDay() - entry.getKey().toEpochDay();
            double weight = daysAgo <= 2 ? 1.5 : 1.0;
            totalWeight += weight;

            DailyTotals totals = entry.getValue();
            averages.weightedCalories += totals.calories * weight;
            averages.weightedProtein += totals.protein * weight;
            averages.weightedFat += totals.fat * weight;
            averages.weightedCarb += totals.carb * weight;
            averages.weightedFiber += totals.fiber * weight;
        }

        if (totalWeight > 0) {
            averages.weightedCalories /= totalWeight;
            averages.weightedProtein /= totalWeight;
            averages.weightedFat /= totalWeight;
            averages.weightedCarb /= totalWeight;
            averages.weightedFiber /= totalWeight;
        }

        return averages;
    }

    private int calculateHistoryCalorieAdjustment(int baseTargetCalories, double weightedAverageCalories) {
        if (weightedAverageCalories <= 0) {
            return 0;
        }

        double diff = weightedAverageCalories - baseTargetCalories;
        if (Math.abs(diff) < 120) {
            return 0;
        }

        int adjustment = (int) Math.round(-diff * 0.35);
        if (adjustment > 300) {
            return 300;
        }
        if (adjustment < -300) {
            return -300;
        }
        return adjustment;
    }

    private double[] adjustedMacroRatios(
            GoalType goalType,
            int baseTargetCalories,
            HistoryAverages history,
            DailyTargetProfile profile
    ) {
        double[] baseRatios = baseMacroRatios(goalType);
        double proteinRatio = baseRatios[0];
        double fatRatio = baseRatios[1];
        double carbRatio = baseRatios[2];

        if (history.weightedCalories > 0) {
            double baseProteinTarget = baseTargetCalories * proteinRatio / 4.0;
            double baseFatTarget = baseTargetCalories * fatRatio / 9.0;
            double baseCarbTarget = baseTargetCalories * carbRatio / 4.0;

            if (history.weightedProtein > 0 && history.weightedProtein < baseProteinTarget * 0.9) {
                proteinRatio += 0.03;
                fatRatio -= 0.01;
                carbRatio -= 0.02;
                profile.addDecisionTag("HISTORY_PROTEIN_UP");
            }
            if (history.weightedFat > baseFatTarget * 1.12) {
                proteinRatio += 0.02;
                fatRatio -= 0.03;
                carbRatio += 0.01;
                profile.addDecisionTag("HISTORY_FAT_DOWN");
            }
            if (history.weightedCarb > baseCarbTarget * 1.12) {
                proteinRatio += 0.02;
                fatRatio += 0.01;
                carbRatio -= 0.03;
                profile.addDecisionTag("HISTORY_CARB_DOWN");
            }
            if (history.weightedFiber > 0 && history.weightedFiber < 18) {
                profile.addDecisionTag("HISTORY_FIBER_LOW");
            }
        }

        if (profile.getHistoryCalorieAdjustment() < 0) {
            profile.addDecisionTag("HISTORY_CAL_DOWN");
        } else if (profile.getHistoryCalorieAdjustment() > 0) {
            profile.addDecisionTag("HISTORY_CAL_UP");
        }

        proteinRatio = clamp(proteinRatio, 0.18, 0.40);
        fatRatio = clamp(fatRatio, 0.18, 0.35);
        carbRatio = Math.max(0.25, 1.0 - proteinRatio - fatRatio);

        double sum = proteinRatio + fatRatio + carbRatio;
        return new double[]{proteinRatio / sum, fatRatio / sum, carbRatio / sum};
    }

    private double[] baseMacroRatios(GoalType goalType) {
        return switch (goalType) {
            case LOSE_FAT -> new double[]{0.35, 0.27, 0.38};
            case GAIN_MUSCLE -> new double[]{0.28, 0.22, 0.50};
            case DIABETES_CONTROL -> new double[]{0.25, 0.30, 0.45};
            case HYPERTENSION_CONTROL -> new double[]{0.25, 0.25, 0.50};
            case HYPERLIPIDEMIA_CONTROL -> new double[]{0.24, 0.23, 0.53};
            case MAINTAIN -> new double[]{0.25, 0.25, 0.50};
        };
    }

    private int goalCalorieAdjustment(GoalType goalType) {
        return switch (goalType) {
            case LOSE_FAT -> -300;
            case GAIN_MUSCLE -> 300;
            case DIABETES_CONTROL -> -200;
            case HYPERTENSION_CONTROL -> -100;
            case HYPERLIPIDEMIA_CONTROL -> -150;
            case MAINTAIN -> 0;
        };
    }

    private double calculateBMR(User user) {
        double bmr = 10 * user.getWeight() + 6.25 * user.getHeight() - 5 * user.getAge();
        return user.getGender() == 1 ? bmr + 5 : bmr - 161;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double safe(Double value) {
        return value == null ? 0.0 : value;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private double decimalValue(BigDecimal value) {
        return value == null ? 0.0 : value.doubleValue();
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static class DailyTotals {
        private double calories;
        private double protein;
        private double fat;
        private double carb;
        private double fiber;
    }

    private static class HistoryAverages {
        private double weightedCalories;
        private double weightedProtein;
        private double weightedFat;
        private double weightedCarb;
        private double weightedFiber;
    }
}
