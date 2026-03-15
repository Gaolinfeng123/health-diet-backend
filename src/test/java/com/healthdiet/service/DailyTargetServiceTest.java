package com.healthdiet.service;

import com.healthdiet.entity.DietRecord;
import com.healthdiet.entity.Food;
import com.healthdiet.entity.User;
import com.healthdiet.mapper.DietRecordMapper;
import com.healthdiet.mapper.FoodMapper;
import com.healthdiet.recommend.enums.GoalType;
import com.healthdiet.recommend.model.DailyTargetProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

class DailyTargetServiceTest {

    private DailyTargetService dailyTargetService;
    private DietRecordMapper dietRecordMapper;
    private FoodMapper foodMapper;

    @BeforeEach
    void setUp() {
        dailyTargetService = new DailyTargetService();
        dietRecordMapper = Mockito.mock(DietRecordMapper.class);
        foodMapper = Mockito.mock(FoodMapper.class);
        ReflectionTestUtils.setField(dailyTargetService, "dietRecordMapper", dietRecordMapper);
        ReflectionTestUtils.setField(dailyTargetService, "foodMapper", foodMapper);
    }

    @Test
    void shouldUseDifferentActivityFactorsForDifferentUsers() {
        Mockito.when(dietRecordMapper.selectList(any())).thenReturn(List.of());

        User sedentary = baseUser();
        sedentary.setActivityLevel(1);

        User highActivity = baseUser();
        highActivity.setActivityLevel(4);

        DailyTargetProfile sedentaryTarget = dailyTargetService.buildDailyTarget(
                sedentary, GoalType.MAINTAIN, LocalDate.of(2026, 3, 15)
        );
        DailyTargetProfile highTarget = dailyTargetService.buildDailyTarget(
                highActivity, GoalType.MAINTAIN, LocalDate.of(2026, 3, 15)
        );

        assertEquals(1.2, sedentaryTarget.getActivityFactor());
        assertEquals(1.7, highTarget.getActivityFactor());
        assertTrue(highTarget.getTargetCalories() > sedentaryTarget.getTargetCalories());
    }

    @Test
    void shouldAdjustCaloriesDownAndProteinUpWhenRecentIntakeIsHighFatLowProtein() {
        LocalDate targetDate = LocalDate.of(2026, 3, 15);
        List<DietRecord> records = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            DietRecord record = new DietRecord();
            record.setUserId(1L);
            record.setFoodId(100L);
            record.setDate(targetDate.minusDays(i));
            record.setQuantity(10);
            records.add(record);
        }

        Food food = new Food();
        food.setId(100L);
        food.setCalories(260.0);
        food.setProtein(6.0);
        food.setFat(18.0);
        food.setCarb(22.0);
        food.setFiber(BigDecimal.valueOf(1.5));

        Mockito.when(dietRecordMapper.selectList(any())).thenReturn(records);
        Mockito.when(foodMapper.selectBatchIds(any())).thenReturn(List.of(food));

        User user = baseUser();
        user.setActivityLevel(2);

        DailyTargetProfile target = dailyTargetService.buildDailyTarget(user, GoalType.MAINTAIN, targetDate);

        assertTrue(target.getHistoryCalorieAdjustment() < 0);
        assertTrue(target.hasTag("HISTORY_CAL_DOWN"));
        assertTrue(target.hasTag("HISTORY_PROTEIN_UP"));
        assertTrue(target.hasTag("HISTORY_FAT_DOWN"));
        assertTrue(target.getTargetProteinG() > 100.0);
    }

    private User baseUser() {
        User user = new User();
        user.setId(1L);
        user.setHeight(175.0);
        user.setWeight(70.0);
        user.setAge(25);
        user.setGender(1);
        user.setTarget(0);
        return user;
    }
}
