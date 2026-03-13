package com.healthdiet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.healthdiet.entity.DietRecord;
import com.healthdiet.entity.Food;
import com.healthdiet.mapper.DietRecordMapper;
import com.healthdiet.mapper.FoodMapper;
import com.healthdiet.service.IDietRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DietRecordServiceImpl extends ServiceImpl<DietRecordMapper, DietRecord> implements IDietRecordService {

    @Autowired
    private FoodMapper foodMapper;

    @Override
    public List<DietRecord> listRecords(Long userId, String date) {
        QueryWrapper<DietRecord> query = new QueryWrapper<>();
        query.eq("user_id", userId);

        if (date != null && !date.isEmpty()) {
            query.eq("date", date);
        }

        query.orderByDesc("date");
        List<DietRecord> records = this.list(query);

        fillFoodInfo(records);
        return records;
    }

    @Override
    public IPage<DietRecord> listRecordsByPage(int pageNum, int pageSize, Long queryUserId, String date) {
        Page<DietRecord> page = new Page<>(pageNum, pageSize);

        QueryWrapper<DietRecord> query = new QueryWrapper<>();

        if (queryUserId != null) {
            query.eq("user_id", queryUserId);
        }

        if (date != null && !date.isEmpty()) {
            query.eq("date", date);
        }

        query.orderByDesc("date", "id");

        this.page(page, query);

        fillFoodInfo(page.getRecords());
        return page;
    }

    /**
     * 统一口径：
     * foods 表的 calories / protein / fat / carb 全部表示每100g
     * diet_records.quantity 表示“多少个100g单位”
     *
     * 所以：
     * 总热量 = 每100g热量 * quantity
     */
    private void fillFoodInfo(List<DietRecord> records) {
        for (DietRecord record : records) {
            Food food = foodMapper.selectById(record.getFoodId());
            if (food != null) {
                record.setFoodName(food.getName());
                record.setFoodCalories(food.getCalories()); // 每100g热量
                record.setTotalCalories(food.getCalories() * safeInt(record.getQuantity()));
            }
        }
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}