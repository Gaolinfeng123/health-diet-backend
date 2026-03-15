package com.healthdiet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.healthdiet.entity.AnalysisReport;
import com.healthdiet.entity.DietRecord;
import com.healthdiet.entity.Food;
import com.healthdiet.entity.User;
import com.healthdiet.entity.vo.TrendPointVO;
import com.healthdiet.mapper.DietRecordMapper;
import com.healthdiet.mapper.FoodMapper;
import com.healthdiet.mapper.UserMapper;
import com.healthdiet.recommend.enums.GoalType;
import com.healthdiet.recommend.model.DailyTargetProfile;
import com.healthdiet.service.AiService;
import com.healthdiet.service.DailyTargetService;
import com.healthdiet.service.IAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AnalysisServiceImpl implements IAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisServiceImpl.class);

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private DietRecordMapper dietRecordMapper;

    @Autowired
    private FoodMapper foodMapper;

    @Autowired
    private DailyTargetService dailyTargetService;

    @Autowired
    private AiService aiService;

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
            Set<Long> foodIds = records.stream().map(DietRecord::getFoodId).collect(Collectors.toSet());
            foodMap = foodMapper.selectBatchIds(foodIds)
                    .stream()
                    .collect(Collectors.toMap(Food::getId, food -> food));
        }

        Map<LocalDate, Double> dayCaloriesMap = new HashMap<>();
        for (int i = 0; i < days; i++) {
            dayCaloriesMap.put(startDate.plusDays(i), 0.0);
        }

        for (DietRecord record : records) {
            Food food = foodMap.get(record.getFoodId());
            if (food == null) {
                continue;
            }
            double cal = safe(food.getCalories()) * safeInt(record.getQuantity());
            dayCaloriesMap.merge(record.getDate(), cal, Double::sum);
        }

        List<TrendPointVO> result = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate date = startDate.plusDays(i);
            result.add(new TrendPointVO(date.toString(), round1(dayCaloriesMap.getOrDefault(date, 0.0))));
        }
        return result;
    }

    @Override
    public AnalysisReport analyze(Long userId, String date) {
        long startedAt = System.nanoTime();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        LocalDate targetDate = LocalDate.parse(date);
        GoalType goalType = GoalType.fromCode(user.getTarget());
        DailyTargetProfile targetProfile = dailyTargetService.buildDailyTarget(user, goalType, targetDate);
        long targetStageMs = elapsedMillis(startedAt);

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
            Set<Long> foodIds = records.stream().map(DietRecord::getFoodId).collect(Collectors.toSet());
            Map<Long, Food> foodMap = foodMapper.selectBatchIds(foodIds)
                    .stream()
                    .collect(Collectors.toMap(Food::getId, food -> food));

            for (DietRecord record : records) {
                Food food = foodMap.get(record.getFoodId());
                if (food == null) {
                    continue;
                }

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
        long nutritionStageMs = elapsedMillis(startedAt) - targetStageMs;

        AnalysisReport.MacrosRatio actualPfcRatio = buildPfcRatio(actualPro, actualFat, actualCarb);
        AnalysisReport.MacrosRatio targetPfcRatio = buildPfcRatio(
                targetProfile.getTargetProteinG(),
                targetProfile.getTargetFatG(),
                targetProfile.getTargetCarbG()
        );

        AnalysisReport report = new AnalysisReport();
        report.setAnalysisDate(targetDate.toString());

        double bmi = calculateBMI(user);
        report.setBmi(round1(bmi));
        report.setStatus(getBMIStatus(bmi));
        report.setActivityFactor(round1(targetProfile.getActivityFactor()));
        report.setTdee(round1(targetProfile.getTdee()));

        report.setTotalCalories(round1(actualCal));
        report.setRecommendCalories(round1(targetProfile.getTargetCalories()));
        report.setDiff(round1(actualCal - targetProfile.getTargetCalories()));

        report.setTotalProtein(round1(actualPro));
        report.setTotalFat(round1(actualFat));
        report.setTotalCarb(round1(actualCarb));

        report.setTargetProtein(round1(targetProfile.getTargetProteinG()));
        report.setTargetFat(round1(targetProfile.getTargetFatG()));
        report.setTargetCarb(round1(targetProfile.getTargetCarbG()));

        report.setActualPfcRatio(actualPfcRatio);
        report.setTargetPfcRatio(targetPfcRatio);

        report.setBreakfastCal(round1(breakfastCal));
        report.setLunchCal(round1(lunchCal));
        report.setDinnerCal(round1(dinnerCal));
        report.setSnackCal(round1(snackCal));
        report.setReportTitle(targetDate + " 饮食分析报告");
        report.setEnergyAssessment(buildEnergyAssessment(actualCal, targetProfile.getTargetCalories()));
        report.setNutrientAssessments(buildNutrientAssessments(
                actualPro,
                actualFat,
                actualCarb,
                targetProfile.getTargetProteinG(),
                targetProfile.getTargetFatG(),
                targetProfile.getTargetCarbG()
        ));
        report.setMealAssessments(buildMealAssessments(
                breakfastCal,
                lunchCal,
                dinnerCal,
                snackCal,
                actualCal,
                targetProfile.getTargetCalories()
        ));
        report.setHighlights(buildHighlights(
                goalType,
                actualCal,
                targetProfile.getTargetCalories(),
                actualPro,
                actualFat,
                actualCarb,
                targetProfile.getTargetProteinG(),
                targetProfile.getTargetFatG(),
                targetProfile.getTargetCarbG(),
                breakfastCal,
                lunchCal,
                dinnerCal,
                snackCal
        ));
        report.setSuggestions(buildSuggestions(
                goalType,
                actualCal,
                targetProfile.getTargetCalories(),
                actualPro,
                actualFat,
                actualCarb,
                targetProfile.getTargetProteinG(),
                targetProfile.getTargetFatG(),
                targetProfile.getTargetCarbG(),
                breakfastCal,
                lunchCal,
                dinnerCal,
                snackCal
        ));
        report.setOverview(buildOverview(goalType, report));
        report.setAdvice(buildAdvice(report));
        long reportStageMs = elapsedMillis(startedAt) - targetStageMs - nutritionStageMs;
        report.setQuickQuestions(aiService.generateQuickQuestions(user, report));
        long quickQuestionStageMs = elapsedMillis(startedAt) - targetStageMs - nutritionStageMs - reportStageMs;
        logAnalysisCost(userId, targetDate, targetStageMs, nutritionStageMs, reportStageMs, quickQuestionStageMs, elapsedMillis(startedAt));

        return report;
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

    private AnalysisReport.EnergyAssessment buildEnergyAssessment(double actualCal, double targetCal) {
        AnalysisReport.EnergyAssessment assessment = new AnalysisReport.EnergyAssessment();
        double diff = actualCal - targetCal;
        assessment.setActual(round1(actualCal));
        assessment.setTarget(round1(targetCal));
        assessment.setDiff(round1(diff));
        if (Math.abs(diff) <= Math.max(120, targetCal * 0.08)) {
            assessment.setStatus("达标");
            assessment.setComment("当天总热量和目标较接近，整体能量控制比较稳定。");
        } else if (diff > 0) {
            assessment.setStatus("偏高");
            assessment.setComment("当天总热量比目标高 " + Math.round(diff) + " 千卡，整体摄入略多。");
        } else {
            assessment.setStatus("偏低");
            assessment.setComment("当天总热量比目标低 " + Math.round(Math.abs(diff)) + " 千卡，整体摄入偏少。");
        }
        return assessment;
    }

    private List<AnalysisReport.NutrientAssessment> buildNutrientAssessments(
            double actualPro,
            double actualFat,
            double actualCarb,
            double targetProtein,
            double targetFat,
            double targetCarb
    ) {
        List<AnalysisReport.NutrientAssessment> assessments = new ArrayList<>();
        assessments.add(buildNutrientAssessment("蛋白质", actualPro, targetProtein, 0.10, "建议优先补优质蛋白。", "蛋白质供给比较到位。"));
        assessments.add(buildNutrientAssessment("脂肪", actualFat, targetFat, 0.15, "可以适当补一点优质脂肪。", "脂肪摄入处于合理范围。"));
        assessments.add(buildNutrientAssessment("碳水", actualCarb, targetCarb, 0.10, "主食结构需要再平稳一些。", "碳水摄入与目标较接近。"));
        return assessments;
    }

    private AnalysisReport.NutrientAssessment buildNutrientAssessment(
            String nutrient,
            double actual,
            double target,
            double toleranceRatio,
            String lowComment,
            String matchedComment
    ) {
        AnalysisReport.NutrientAssessment assessment = new AnalysisReport.NutrientAssessment();
        double diff = actual - target;
        double tolerance = Math.max(5.0, target * toleranceRatio);
        assessment.setNutrient(nutrient);
        assessment.setActual(round1(actual));
        assessment.setTarget(round1(target));
        assessment.setDiff(round1(diff));
        if (Math.abs(diff) <= tolerance) {
            assessment.setStatus("达标");
            assessment.setComment(matchedComment);
        } else if (diff > 0) {
            assessment.setStatus("偏高");
            assessment.setComment(nutrient + "比目标高 " + Math.round(diff) + "g，后续需要适度回收。");
        } else {
            assessment.setStatus("偏低");
            assessment.setComment(nutrient + "比目标低 " + Math.round(Math.abs(diff)) + "g，" + lowComment);
        }
        return assessment;
    }

    private List<AnalysisReport.MealAssessment> buildMealAssessments(
            double breakfastCal,
            double lunchCal,
            double dinnerCal,
            double snackCal,
            double totalCalories,
            double recommendCalories
    ) {
        List<AnalysisReport.MealAssessment> assessments = new ArrayList<>();
        assessments.add(buildMealAssessment("breakfast", "早餐", breakfastCal, recommendCalories * 0.30, totalCalories, "早餐负责打开全天供能。"));
        assessments.add(buildMealAssessment("lunch", "午餐", lunchCal, recommendCalories * 0.40, totalCalories, "午餐通常承担白天主要能量。"));
        assessments.add(buildMealAssessment("dinner", "晚餐", dinnerCal, recommendCalories * 0.30, totalCalories, "晚餐建议更克制一些。"));
        assessments.add(buildSnackAssessment(snackCal, totalCalories));
        return assessments;
    }

    private AnalysisReport.MealAssessment buildMealAssessment(
            String type,
            String title,
            double calories,
            double targetCalories,
            double totalCalories,
            String baseComment
    ) {
        AnalysisReport.MealAssessment assessment = new AnalysisReport.MealAssessment();
        assessment.setType(type);
        assessment.setTitle(title);
        assessment.setCalories(round1(calories));
        assessment.setTargetCalories(round1(targetCalories));
        assessment.setShare(totalCalories <= 0 ? 0.0 : round3(calories / totalCalories));
        if (calories <= 0) {
            assessment.setStatus("未记录");
            assessment.setComment(title + "没有记录，" + baseComment);
            return assessment;
        }
        double ratio = targetCalories <= 0 ? 1.0 : calories / targetCalories;
        if (ratio < 0.70) {
            assessment.setStatus("偏低");
            assessment.setComment(title + "供能偏少，" + baseComment);
        } else if (ratio > 1.30) {
            assessment.setStatus("偏高");
            assessment.setComment(title + "供能偏重，" + baseComment);
        } else {
            assessment.setStatus("合理");
            assessment.setComment(title + "热量分配基本合理，" + baseComment);
        }
        return assessment;
    }

    private AnalysisReport.MealAssessment buildSnackAssessment(double snackCal, double totalCalories) {
        AnalysisReport.MealAssessment assessment = new AnalysisReport.MealAssessment();
        assessment.setType("snack");
        assessment.setTitle("加餐");
        assessment.setCalories(round1(snackCal));
        assessment.setTargetCalories(0.0);
        assessment.setShare(totalCalories <= 0 ? 0.0 : round3(snackCal / totalCalories));
        if (snackCal <= 0) {
            assessment.setStatus("未安排");
            assessment.setComment("当天未安排加餐，整体结构以三餐为主。");
        } else {
            assessment.setStatus("已安排");
            assessment.setComment("当天存在加餐记录，说明主餐之外还有补充摄入。");
        }
        return assessment;
    }

    private List<String> buildHighlights(
            GoalType goalType,
            double actualCal,
            double targetCal,
            double actualPro,
            double actualFat,
            double actualCarb,
            double targetProtein,
            double targetFat,
            double targetCarb,
            double breakfastCal,
            double lunchCal,
            double dinnerCal,
            double snackCal
    ) {
        List<String> highlights = new ArrayList<>();
        double energyDiff = actualCal - targetCal;
        if (Math.abs(energyDiff) <= Math.max(120, targetCal * 0.08)) {
            highlights.add("总热量和目标接近，整体能量控制比较稳定。");
        } else if (energyDiff > 0) {
            highlights.add("总热量偏高 " + Math.round(energyDiff) + " 千卡，当天摄入略多。");
        } else {
            highlights.add("总热量偏低 " + Math.round(Math.abs(energyDiff)) + " 千卡，当天摄入偏少。");
        }
        highlights.add(mainMacroHighlight(actualPro, actualFat, actualCarb, targetProtein, targetFat, targetCarb));
        if (dinnerCal > 0 && dinnerCal >= lunchCal) {
            highlights.add("晚餐热量不低于午餐，晚间摄入偏重。");
        } else if (lunchCal > 0) {
            highlights.add("午餐承担了主要能量，白天供能结构相对合理。");
        }
        if (snackCal <= 0 && actualCal < targetCal - 120) {
            highlights.add("当天没有加餐，缺口主要来自正餐或加餐不足。");
        }
        highlights.add(goalFocusHighlight(goalType));
        return highlights;
    }

    private String mainMacroHighlight(
            double actualPro,
            double actualFat,
            double actualCarb,
            double targetProtein,
            double targetFat,
            double targetCarb
    ) {
        double proteinGap = Math.abs(actualPro - targetProtein);
        double fatGap = Math.abs(actualFat - targetFat);
        double carbGap = Math.abs(actualCarb - targetCarb);
        if (proteinGap >= fatGap && proteinGap >= carbGap) {
            return actualPro >= targetProtein ? "蛋白质整体到位，恢复支持相对充足。" : "蛋白质是当天最需要补强的一项。";
        }
        if (fatGap >= carbGap) {
            return actualFat >= targetFat ? "脂肪偏高，优先检查油脂和高脂肉类。" : "脂肪偏低，可以适当补一点优质脂肪。";
        }
        return actualCarb >= targetCarb ? "碳水偏高，主食结构需要再平稳一些。" : "碳水略低，当天主食摄入偏保守。";
    }

    private String goalFocusHighlight(GoalType goalType) {
        return switch (goalType) {
            case LOSE_FAT -> "减脂目标下，建议继续优先保证蛋白并控制晚餐总量。";
            case GAIN_MUSCLE -> "增肌目标下，更需要保证全天热量和蛋白持续达标。";
            case DIABETES_CONTROL -> "控糖目标下，重点仍是低 GI 主食和更平稳的碳水分配。";
            case HYPERTENSION_CONTROL -> "控压目标下，要继续关注钠摄入和加工食品比例。";
            case HYPERLIPIDEMIA_CONTROL -> "控脂目标下，要继续控制饱和脂肪和油炸类食物。";
            case MAINTAIN -> "维持目标下，重点是保持全天结构稳定且不过度波动。";
        };
    }

    private List<String> buildSuggestions(
            GoalType goalType,
            double actualCal,
            double targetCal,
            double actualPro,
            double actualFat,
            double actualCarb,
            double targetProtein,
            double targetFat,
            double targetCarb,
            double breakfastCal,
            double lunchCal,
            double dinnerCal,
            double snackCal
    ) {
        Set<String> suggestions = new java.util.LinkedHashSet<>();
        if (actualCal < targetCal - 120) {
            suggestions.add("明天可以把主食或正餐份量适当补回来，避免全天热量持续偏低。");
        } else if (actualCal > targetCal + 120) {
            suggestions.add("明天先从收紧高能量零食、油炸食物或过量主食开始。");
        }
        if (actualPro < targetProtein - 8) {
            suggestions.add("每餐至少保留一份优质蛋白，例如鱼虾、鸡胸、蛋类或豆制品。");
        }
        if (actualFat < targetFat - 6) {
            suggestions.add("脂肪偏低时，可少量补充坚果、牛油果或深海鱼等优质脂肪。");
        } else if (actualFat > targetFat + 6) {
            suggestions.add("脂肪偏高时，优先减少油炸、肥肉和高脂加工食品。");
        }
        if (actualCarb > targetCarb + 10) {
            suggestions.add("主食可以继续偏向糙米、燕麦、荞麦等低 GI 选择，减少精制碳水。");
        } else if (actualCarb < targetCarb - 10) {
            suggestions.add("如果近期训练或活动量较大，主食可以略微补足，避免能量不足。");
        }
        if (breakfastCal <= 0) {
            suggestions.add("早餐建议尽量补上，避免上午供能不足和后续进食失衡。");
        } else if (breakfastCal < targetCal * 0.20) {
            suggestions.add("早餐偏轻，可以增加一份主食或蛋白，提高上午稳定性。");
        }
        if (dinnerCal >= lunchCal && dinnerCal > 0) {
            suggestions.add("晚餐热量不宜超过午餐，主食和高脂食物可以再收一点。");
        }
        if (snackCal <= 0 && actualCal < targetCal - 120) {
            suggestions.add("如果正餐吃不够，可以安排一次受控加餐来补足缺口。");
        }
        switch (goalType) {
            case DIABETES_CONTROL -> suggestions.add("控糖阶段优先保持碳水分布均匀，避免单餐主食过重。");
            case GAIN_MUSCLE -> suggestions.add("增肌阶段建议把蛋白分配到三餐和加餐，而不是集中在一餐。");
            case HYPERTENSION_CONTROL -> suggestions.add("控压阶段继续控制高钠加工食品和重口味调味。");
            case HYPERLIPIDEMIA_CONTROL -> suggestions.add("控脂阶段更适合蒸煮炖，减少煎炸和高脂蘸料。");
            case LOSE_FAT -> suggestions.add("减脂阶段保持蛋白和蔬菜优先，晚餐更克制会更稳。");
            case MAINTAIN -> suggestions.add("维持阶段重点是把每餐结构做稳定，不要忽高忽低。");
        }
        while (suggestions.size() < 4) {
            suggestions.add("继续保持规律进餐和饮水，能让第二天的饮食结构更稳定。");
        }
        return new ArrayList<>(suggestions);
    }

    private String buildOverview(GoalType goalType, AnalysisReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append(report.getEnergyAssessment().getComment());
        AnalysisReport.NutrientAssessment mainIssue = report.getNutrientAssessments().stream()
                .filter(item -> !"达标".equals(item.getStatus()))
                .findFirst()
                .orElse(null);
        if (mainIssue != null) {
            builder.append(" ").append(mainIssue.getComment());
        }
        builder.append(" ").append(goalFocusHighlight(goalType));
        return builder.toString().trim();
    }

    private String buildAdvice(AnalysisReport report) {
        if (report.getHighlights() != null && !report.getHighlights().isEmpty()) {
            return report.getHighlights().get(0);
        }
        return report.getOverview();
    }

    private long elapsedMillis(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }

    private void logAnalysisCost(
            Long userId,
            LocalDate targetDate,
            long targetStageMs,
            long nutritionStageMs,
            long reportStageMs,
            long quickQuestionStageMs,
            long totalStageMs
    ) {
        if (totalStageMs < 1000) {
            return;
        }
        log.warn(
                "analysis report slow userId={} date={} total={}ms target={}ms nutrition={}ms report={}ms quickQuestions={}ms",
                userId,
                targetDate,
                totalStageMs,
                targetStageMs,
                nutritionStageMs,
                reportStageMs,
                quickQuestionStageMs
        );
    }

    private double calculateBMI(User user) {
        double h = user.getHeight() / 100.0;
        return user.getWeight() / (h * h);
    }

    private String getBMIStatus(double bmi) {
        if (bmi < 18.5) {
            return "underweight";
        }
        if (bmi < 24) {
            return "normal";
        }
        if (bmi < 28) {
            return "overweight";
        }
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
}
