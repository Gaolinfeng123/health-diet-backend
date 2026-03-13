package com.healthdiet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.healthdiet.entity.AnalysisReport;
import com.healthdiet.entity.DietRecord;
import com.healthdiet.entity.Food;
import com.healthdiet.entity.User;
import com.healthdiet.mapper.DietRecordMapper;
import com.healthdiet.mapper.FoodMapper;
import com.healthdiet.mapper.UserMapper;
import com.healthdiet.service.ConfigService;
import com.healthdiet.service.IAnalysisService;
import com.healthdiet.entity.vo.TrendPointVO;
import java.util.ArrayList;
import java.util.HashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AnalysisServiceImpl implements IAnalysisService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private DietRecordMapper dietRecordMapper;

    @Autowired
    private FoodMapper foodMapper;

    @Autowired
    private ConfigService configService;

    @Override
    public List<TrendPointVO> getCalorieTrend(Long userId, int days) {
        if (days <= 0) {
            days = 7;
        }

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1L);

        QueryWrapper<DietRecord> query = new QueryWrapper<>();
        query.eq("user_id", userId)
                .between("date", startDate, endDate)
                .orderByAsc("date");

        List<DietRecord> records = dietRecordMapper.selectList(query);

        Map<Long, Food> foodMap = new HashMap<>();
        if (!records.isEmpty()) {
            Set<Long> foodIds = records.stream()
                    .map(DietRecord::getFoodId)
                    .collect(Collectors.toSet());

            foodMap = foodMapper.selectBatchIds(foodIds)
                    .stream()
                    .collect(Collectors.toMap(Food::getId, f -> f));
        }

        // 先为每一天补0，保证没有记录的日期也能返回
        Map<LocalDate, Double> dayCaloriesMap = new HashMap<>();
        for (int i = 0; i < days; i++) {
            LocalDate date = startDate.plusDays(i);
            dayCaloriesMap.put(date, 0.0);
        }

        for (DietRecord record : records) {
            Food food = foodMap.get(record.getFoodId());
            if (food == null) {
                continue;
            }

            // 统一口径：quantity = 100g单位数
            double cal = safe(food.getCalories()) * safeInt(record.getQuantity());
            dayCaloriesMap.merge(record.getDate(), cal, Double::sum);
        }

        List<TrendPointVO> result = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate date = startDate.plusDays(i);
            result.add(new TrendPointVO(
                    date.toString(),
                    round1(dayCaloriesMap.getOrDefault(date, 0.0))
            ));
        }

        return result;
    }

    public AnalysisReport analyze(Long userId, String date) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        LocalDate targetDate = LocalDate.parse(date);

        // 1. 计算分析基准：目标热量 + 目标宏量营养素
        double bmr = calculateBMR(user);
        double activityFactor = configService.getDouble("BMR_ACTIVITY_FACTOR", 1.3);
        double tdee = bmr * activityFactor;

        int goalType = user.getTarget() != null ? user.getTarget() : 0;
        double targetCal = tdee;

        switch (goalType) {
            case -1 -> targetCal -= 300; // 减脂
            case 1 -> targetCal += 300;  // 增肌
            case 2 -> targetCal -= 200;  // 糖尿病控糖
            case 3 -> targetCal -= 100;  // 高血压
            case 4 -> targetCal -= 150;  // 高血脂
            default -> {
            }
        }

        if (targetCal < bmr) {
            targetCal = bmr;
        }

        MacroTarget macroTarget = buildMacroTarget(goalType, targetCal);

        // 2. 查询当天饮食记录
        QueryWrapper<DietRecord> query = new QueryWrapper<>();
        query.eq("user_id", userId).eq("date", targetDate);
        List<DietRecord> records = dietRecordMapper.selectList(query);

        double actualCal = 0.0;
        double actualPro = 0.0;
        double actualFat = 0.0;
        double actualCarb = 0.0;

        double breakfastCal = 0.0;
        double lunchCal = 0.0;
        double dinnerCal = 0.0;
        double snackCal = 0.0;

        if (!records.isEmpty()) {
            Set<Long> foodIds = records.stream()
                    .map(DietRecord::getFoodId)
                    .collect(Collectors.toSet());

            Map<Long, Food> foodMap = foodMapper.selectBatchIds(foodIds)
                    .stream()
                    .collect(Collectors.toMap(Food::getId, f -> f));

            for (DietRecord record : records) {
                Food food = foodMap.get(record.getFoodId());
                if (food == null) {
                    continue;
                }

                // 统一口径：quantity = 100g 单位数
                double qty = safeInt(record.getQuantity());

                double cal = safe(food.getCalories()) * qty;
                double pro = safe(food.getProtein()) * qty;
                double fat = safe(food.getFat()) * qty;
                double carb = safe(food.getCarb()) * qty;

                actualCal += cal;
                actualPro += pro;
                actualFat += fat;
                actualCarb += carb;

                if (record.getMealType() != null) {
                    switch (record.getMealType()) {
                        case 1 -> breakfastCal += cal;
                        case 2 -> lunchCal += cal;
                        case 3 -> dinnerCal += cal;
                        case 4 -> snackCal += cal;
                        default -> {
                        }
                    }
                }
            }
        }

        // 3. 计算实际PFC和目标PFC
        AnalysisReport.MacrosRatio actualPfcRatio = buildPfcRatio(actualPro, actualFat, actualCarb);
        AnalysisReport.MacrosRatio targetPfcRatio = buildPfcRatio(
                macroTarget.targetProtein,
                macroTarget.targetFat,
                macroTarget.targetCarb
        );

        // 4. 组装报告
        AnalysisReport report = new AnalysisReport();
        report.setAnalysisDate(targetDate.toString());

        double bmi = calculateBMI(user);
        report.setBmi(round1(bmi));
        report.setStatus(getBMIStatus(bmi));

        report.setTotalCalories(round1(actualCal));
        report.setRecommendCalories(round1(targetCal));
        report.setDiff(round1(actualCal - targetCal));

        report.setTotalProtein(round1(actualPro));
        report.setTotalFat(round1(actualFat));
        report.setTotalCarb(round1(actualCarb));

        report.setTargetProtein(round1(macroTarget.targetProtein));
        report.setTargetFat(round1(macroTarget.targetFat));
        report.setTargetCarb(round1(macroTarget.targetCarb));

        report.setActualPfcRatio(actualPfcRatio);
        report.setTargetPfcRatio(targetPfcRatio);

        report.setBreakfastCal(round1(breakfastCal));
        report.setLunchCal(round1(lunchCal));
        report.setDinnerCal(round1(dinnerCal));
        report.setSnackCal(round1(snackCal));

        report.setAdvice(buildAdvice(
                goalType,
                targetCal,
                actualCal,
                actualFat,
                actualCarb,
                actualPro,
                macroTarget.targetProtein,
                macroTarget.targetFat,
                macroTarget.targetCarb
        ));

        return report;
    }

    /**
     * 按目标类型生成目标宏量营养素
     * 与推荐模块保持同一套思路
     */
    private MacroTarget buildMacroTarget(int goalType, double targetCal) {
        double pRatio;
        double fRatio;
        double cRatio;

        switch (goalType) {
            case -1 -> { // 减脂
                pRatio = 0.35;
                fRatio = 0.27;
                cRatio = 1.0 - pRatio - fRatio;
            }
            case 1 -> { // 增肌
                pRatio = 0.28;
                fRatio = 0.22;
                cRatio = 0.50;
            }
            case 2 -> { // 糖尿病控糖
                pRatio = 0.25;
                fRatio = 0.30;
                cRatio = 0.45;
            }
            case 3 -> { // 高血压
                pRatio = 0.25;
                fRatio = 0.25;
                cRatio = 0.50;
            }
            case 4 -> { // 高血脂
                pRatio = 0.24;
                fRatio = 0.23;
                cRatio = 0.53;
            }
            default -> { // 维持
                pRatio = 0.25;
                fRatio = 0.25;
                cRatio = 0.50;
            }
        }

        MacroTarget target = new MacroTarget();
        target.targetProtein = targetCal * pRatio / 4.0;
        target.targetFat = targetCal * fRatio / 9.0;
        target.targetCarb = targetCal * cRatio / 4.0;
        return target;
    }

    private AnalysisReport.MacrosRatio buildPfcRatio(double protein, double fat, double carb) {
        double totalMacroCalories = protein * 4 + fat * 9 + carb * 4;
        if (totalMacroCalories <= 0) {
            return new AnalysisReport.MacrosRatio(0.0, 0.0, 0.0);
        }

        return new AnalysisReport.MacrosRatio(
                round3(protein * 4 / totalMacroCalories),
                round3(fat * 9 / totalMacroCalories),
                round3(carb * 4 / totalMacroCalories)
        );
    }

    private String buildAdvice(
            int goalType,
            double targetCal,
            double actualCal,
            double actualFat,
            double actualCarb,
            double actualPro,
            double targetProtein,
            double targetFat,
            double targetCarb
    ) {
        StringBuilder advice = new StringBuilder();

        double diff = actualCal - targetCal;

        if (Math.abs(diff) < 150) {
            advice.append("昨日热量摄入基本达标。");
        } else if (diff > 0) {
            advice.append("昨日热量超标 ").append((int) Math.round(diff)).append(" 千卡，建议适当减少主食或高能量食物。");
        } else {
            advice.append("昨日热量不足 ").append((int) Math.round(Math.abs(diff))).append(" 千卡，可考虑适当加餐或提高正餐质量。");
        }

        double totalMacroCalories = actualPro * 4 + actualFat * 9 + actualCarb * 4;
        double fatRatio = totalMacroCalories > 0 ? (actualFat * 9 / totalMacroCalories) : 0;
        double carbRatio = totalMacroCalories > 0 ? (actualCarb * 4 / totalMacroCalories) : 0;

        switch (goalType) {
            case -1 -> {
                if (actualPro < targetProtein) {
                    advice.append(" 当前蛋白质偏低，减脂期建议优先保证优质蛋白。");
                }
                if (fatRatio > 0.30) {
                    advice.append(" 当前脂肪供能占比偏高，建议减少油炸和高脂肉类。");
                }
            }
            case 1 -> {
                if (actualPro < targetProtein) {
                    advice.append(" 当前蛋白质低于目标，增肌期建议增加蛋白摄入。");
                }
                if (actualCal < targetCal) {
                    advice.append(" 总热量偏低，可能不利于训练恢复和增肌。");
                }
            }
            case 2 -> {
                if (carbRatio > 0.50) {
                    advice.append(" 当前碳水占比偏高，控糖阶段建议优先低GI主食并增加膳食纤维。");
                }
                if (actualCarb > targetCarb) {
                    advice.append(" 碳水摄入高于目标，建议减少精制主食和高糖水果。");
                }
            }
            case 3 -> {
                if (fatRatio > 0.35) {
                    advice.append(" 建议减少油腻和加工食品，继续保持清淡饮食。");
                }
                if (actualPro < targetProtein) {
                    advice.append(" 可适当增加低脂优质蛋白，帮助维持整体营养平衡。");
                }
            }
            case 4 -> {
                if (fatRatio > 0.30) {
                    advice.append(" 当前脂肪供能偏高，高血脂阶段建议进一步降低饱和脂肪摄入。");
                }
                if (actualFat > targetFat) {
                    advice.append(" 脂肪摄入高于目标，建议减少高脂肉类和油炸食品。");
                }
            }
            default -> {
                if (fatRatio > 0.40) {
                    advice.append(" 脂肪摄入占比偏高，建议减少油腻食物。");
                }
                if (actualPro < targetProtein) {
                    advice.append(" 蛋白摄入略低，可适当增加优质蛋白。");
                }
            }
        }

        return advice.toString();
    }

    private double calculateBMR(User user) {
        double bmr = 10 * user.getWeight() + 6.25 * user.getHeight() - 5 * user.getAge();
        return user.getGender() == 1 ? bmr + 5 : bmr - 161;
    }

    private double calculateBMI(User user) {
        double h = user.getHeight() / 100.0;
        return user.getWeight() / (h * h);
    }

    private String getBMIStatus(double bmi) {
        if (bmi < 18.5) return "underweight";
        if (bmi < 24) return "normal";
        if (bmi < 28) return "overweight";
        return "obese";
    }

    private double safe(Double value) {
        return value == null ? 0.0 : value;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static class MacroTarget {
        private double targetProtein;
        private double targetFat;
        private double targetCarb;
    }
}