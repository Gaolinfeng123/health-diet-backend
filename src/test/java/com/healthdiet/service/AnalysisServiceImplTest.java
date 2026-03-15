package com.healthdiet.service;

import com.healthdiet.entity.AnalysisReport;
import com.healthdiet.entity.DietRecord;
import com.healthdiet.entity.Food;
import com.healthdiet.entity.User;
import com.healthdiet.mapper.DietRecordMapper;
import com.healthdiet.mapper.FoodMapper;
import com.healthdiet.mapper.UserMapper;
import com.healthdiet.recommend.model.DailyTargetProfile;
import com.healthdiet.service.impl.AnalysisServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;

class AnalysisServiceImplTest {

    private AnalysisServiceImpl analysisService;
    private UserMapper userMapper;
    private DietRecordMapper dietRecordMapper;
    private FoodMapper foodMapper;
    private DailyTargetService dailyTargetService;
    private AiService aiService;

    @BeforeEach
    void setUp() {
        analysisService = new AnalysisServiceImpl();
        userMapper = Mockito.mock(UserMapper.class);
        dietRecordMapper = Mockito.mock(DietRecordMapper.class);
        foodMapper = Mockito.mock(FoodMapper.class);
        dailyTargetService = Mockito.mock(DailyTargetService.class);
        aiService = Mockito.mock(AiService.class);

        ReflectionTestUtils.setField(analysisService, "userMapper", userMapper);
        ReflectionTestUtils.setField(analysisService, "dietRecordMapper", dietRecordMapper);
        ReflectionTestUtils.setField(analysisService, "foodMapper", foodMapper);
        ReflectionTestUtils.setField(analysisService, "dailyTargetService", dailyTargetService);
        ReflectionTestUtils.setField(analysisService, "aiService", aiService);
    }

    @Test
    void analyzeShouldReturnReportStyleFields() {
        LocalDate date = LocalDate.of(2026, 3, 13);

        User user = new User();
        user.setId(1L);
        user.setHeight(175.0);
        user.setWeight(58.0);
        user.setTarget(2);
        user.setActivityLevel(1);

        Food oats = food(3L, "燕麦片(生)", 377, 15, 6.7, 61.6);
        Food egg = food(9L, "鸡蛋(煮)", 144, 13.3, 8.8, 2.8);
        Food brownRice = food(28L, "糙米饭", 111, 2.6, 0.9, 23.0);
        Food fish = food(48L, "沙丁鱼", 208, 24.6, 11.5, 0);

        DietRecord breakfast1 = record(1L, date, 1, 3L, 1);
        DietRecord breakfast2 = record(1L, date, 1, 9L, 1);
        DietRecord lunch1 = record(1L, date, 2, 28L, 2);
        DietRecord lunch2 = record(1L, date, 2, 48L, 1);

        DailyTargetProfile target = new DailyTargetProfile();
        target.setTargetCalories(1600);
        target.setTargetProteinG(90);
        target.setTargetFatG(45);
        target.setTargetCarbG(180);
        target.setActivityFactor(1.2);
        target.setTdee(1850);

        Mockito.when(userMapper.selectById(1L)).thenReturn(user);
        Mockito.when(dietRecordMapper.selectList(any())).thenReturn(List.of(breakfast1, breakfast2, lunch1, lunch2));
        Mockito.when(foodMapper.selectBatchIds(any())).thenReturn(List.of(oats, egg, brownRice, fish));
        Mockito.when(dailyTargetService.buildDailyTarget(any(), any(), any())).thenReturn(target);
        Mockito.when(aiService.generateQuickQuestions(any(), any())).thenReturn(List.of(
                "今天总热量和目标差了多少？",
                "为什么今天更需要关注蛋白质？",
                "早餐还可以怎么优化？",
                "明天主食该怎么选？",
                "如果想加餐，什么更合适？"
        ));

        AnalysisReport report = analysisService.analyze(1L, date.toString());

        assertNotNull(report);
        assertEquals(1.2, report.getActivityFactor());
        assertEquals(1850.0, report.getTdee());
        assertNotNull(report.getReportTitle());
        assertNotNull(report.getOverview());
        assertNotNull(report.getEnergyAssessment());
        assertNotNull(report.getHighlights());
        assertNotNull(report.getNutrientAssessments());
        assertNotNull(report.getMealAssessments());
        assertNotNull(report.getSuggestions());
        assertNotNull(report.getQuickQuestions());
        assertEquals(3, report.getNutrientAssessments().size());
        assertEquals(4, report.getMealAssessments().size());
        assertEquals(5, report.getQuickQuestions().size());
        assertFalse(report.getSuggestions().isEmpty());
    }

    private DietRecord record(Long userId, LocalDate date, int mealType, Long foodId, int quantity) {
        DietRecord record = new DietRecord();
        record.setUserId(userId);
        record.setDate(date);
        record.setMealType(mealType);
        record.setFoodId(foodId);
        record.setQuantity(quantity);
        return record;
    }

    private Food food(Long id, String name, double calories, double protein, double fat, double carb) {
        Food food = new Food();
        food.setId(id);
        food.setName(name);
        food.setCalories(calories);
        food.setProtein(protein);
        food.setFat(fat);
        food.setCarb(carb);
        food.setFiber(BigDecimal.valueOf(3));
        food.setSodiumMg(BigDecimal.valueOf(30));
        food.setGiLevel("low");
        return food;
    }
}
