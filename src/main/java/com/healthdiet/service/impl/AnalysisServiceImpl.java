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

    @Autowired private UserMapper userMapper;
    @Autowired private DietRecordMapper dietRecordMapper;
    @Autowired private FoodMapper foodMapper;
    @Autowired private ConfigService configService; // 注入配置服务

    @Override
    public AnalysisReport analyze(Long userId, String date) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new RuntimeException("用户不存在");

        // --- 1. 计算标准值 (逻辑与推荐模块保持一致) ---
        // BMR (Mifflin-St Jeor)
        double bmr = (10 * user.getWeight()) + (6.25 * user.getHeight()) - (5 * user.getAge());
        bmr = (user.getGender() == 1) ? (bmr + 5) : (bmr - 161);

        // TDEE & 目标热量
        double activityFactor = configService.getDouble("BMR_ACTIVITY_FACTOR", 1.2);
        double tdee = bmr * activityFactor;

        double targetCal = tdee;
        Integer target = user.getTarget() != null ? user.getTarget() : 0;

        // 读取配置
        double cutVal = configService.getDouble("CUT_CALORIES", 400);
        double bulkVal = configService.getDouble("BULK_CALORIES", 300);

        if (target == -1) targetCal -= cutVal;
        else if (target == 1) targetCal += bulkVal;

        // --- 2. 统计实际摄入 ---
        double actualCal = 0.0;
        double actualPro = 0.0;
        double actualFat = 0.0;
        double actualCarb = 0.0;

        double breakfastCal = 0.0;
        double lunchCal = 0.0;
        double dinnerCal = 0.0;
        double snackCal = 0.0;

        QueryWrapper<DietRecord> query = new QueryWrapper<>();
        query.eq("user_id", userId).eq("date", LocalDate.parse(date));
        List<DietRecord> records = dietRecordMapper.selectList(query);

        Set<Long> foodIds = records.stream()
                .map(DietRecord::getFoodId)
                .collect(Collectors.toSet());

        Map<Long, Food> foodMap = foodMapper.selectBatchIds(foodIds)
                .stream()
                .collect(Collectors.toMap(Food::getId, f -> f));

        for (DietRecord record : records) {
            Food food = foodMap.get(record.getFoodId()); // 替换原来的 selectById
            if (food != null) {
                double qty = record.getQuantity(); // 份数
                double cal = food.getCalories() * qty;

                actualCal += cal;
                actualPro += food.getProtein() * qty;
                actualFat += food.getFat() * qty;
                actualCarb += food.getCarb() * qty;

                if (record.getMealType() != null) {
                    switch (record.getMealType()) {
                        case 1 -> breakfastCal += cal;
                        case 2 -> lunchCal += cal;
                        case 3 -> dinnerCal += cal;
                        case 4 -> snackCal += cal;
                    }
                }
            }
        }

        // --- 3. 生成报告 ---
        AnalysisReport report = new AnalysisReport();
        report.setTotalCalories(actualCal);
        report.setRecommendCalories(Math.round(targetCal * 10.0) / 10.0); // 保留一位小数
        report.setDiff(actualCal - targetCal);

        report.setTotalProtein(actualPro);
        report.setTotalFat(actualFat);
        report.setTotalCarb(actualCarb);

        report.setBreakfastCal(breakfastCal);
        report.setLunchCal(lunchCal);
        report.setDinnerCal(dinnerCal);
        report.setSnackCal(snackCal);

        // 生成简短建议
        StringBuilder advice = new StringBuilder();
        double diff = actualCal - targetCal;

        if (Math.abs(diff) < 200) advice.append("今日热量达标，非常棒！");
        else if (diff > 0) advice.append("热量超标 ").append((int)diff).append(" 千卡，注意控制。");
        else advice.append("热量不足 ").append((int)Math.abs(diff)).append(" 千卡，记得加餐。");

        // 脂肪检查
        double fatRatio = (actualCal > 0) ? (actualFat * 9 / actualCal) : 0;
        if (fatRatio > 0.4) advice.append(" 脂肪摄入占比过高(>40%)，请减少油腻食物。");

        report.setAdvice(advice.toString());
        return report;
    }
}