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
    public AnalysisReport analyze(Long userId, String date) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        LocalDate targetDate = LocalDate.parse(date);

        // 1. 计算推荐标准值（尽量与推荐模块保持一致）
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

                // 核心统一口径：
                // quantity = 多少个100g单位
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

        // 3. 生成报告
        AnalysisReport report = new AnalysisReport();
        report.setTotalCalories(round1(actualCal));
        report.setRecommendCalories(round1(targetCal));
        report.setDiff(round1(actualCal - targetCal));

        report.setTotalProtein(round1(actualPro));
        report.setTotalFat(round1(actualFat));
        report.setTotalCarb(round1(actualCarb));

        report.setBreakfastCal(round1(breakfastCal));
        report.setLunchCal(round1(lunchCal));
        report.setDinnerCal(round1(dinnerCal));
        report.setSnackCal(round1(snackCal));

        report.setAdvice(buildAdvice(goalType, targetCal, actualCal, actualFat, actualCarb, actualPro));

        return report;
    }

    private String buildAdvice(int goalType, double targetCal, double actualCal, double actualFat, double actualCarb, double actualPro) {
        StringBuilder advice = new StringBuilder();

        double diff = actualCal - targetCal;

        if (Math.abs(diff) < 150) {
            advice.append("今日热量摄入基本达标。");
        } else if (diff > 0) {
            advice.append("今日热量超标 ").append((int) Math.round(diff)).append(" 千卡，建议适当减少主食或高能量食物。");
        } else {
            advice.append("今日热量不足 ").append((int) Math.round(Math.abs(diff))).append(" 千卡，可考虑适当加餐或提高正餐质量。");
        }

        double totalMacroCalories = actualPro * 4 + actualFat * 9 + actualCarb * 4;
        double fatRatio = totalMacroCalories > 0 ? (actualFat * 9 / totalMacroCalories) : 0;
        double carbRatio = totalMacroCalories > 0 ? (actualCarb * 4 / totalMacroCalories) : 0;

        switch (goalType) {
            case -1 -> {
                if (fatRatio > 0.30) {
                    advice.append(" 当前脂肪供能占比偏高，减脂期建议减少油炸和高脂肉类。");
                }
            }
            case 1 -> {
                if (actualPro < 80) {
                    advice.append(" 当前蛋白质偏低，增肌期建议增加优质蛋白摄入。");
                }
            }
            case 2 -> {
                if (carbRatio > 0.50) {
                    advice.append(" 当前碳水占比偏高，控糖阶段建议优先低GI主食并增加膳食纤维。");
                }
                if (fatRatio > 0.35) {
                    advice.append(" 同时注意减少高脂食物，避免影响代谢控制。");
                }
            }
            case 3 -> {
                if (fatRatio > 0.35) {
                    advice.append(" 建议减少油腻和加工食品，保持清淡饮食。");
                }
            }
            case 4 -> {
                if (fatRatio > 0.30) {
                    advice.append(" 当前脂肪供能偏高，高血脂阶段建议进一步降低饱和脂肪摄入。");
                }
            }
            default -> {
                if (fatRatio > 0.40) {
                    advice.append(" 脂肪摄入占比偏高，建议减少油腻食物。");
                }
            }
        }

        return advice.toString();
    }

    private double calculateBMR(User user) {
        double bmr = 10 * user.getWeight() + 6.25 * user.getHeight() - 5 * user.getAge();
        return user.getGender() == 1 ? bmr + 5 : bmr - 161;
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
}