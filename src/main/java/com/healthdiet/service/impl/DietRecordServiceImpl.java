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

    /**
     * 方法 1: 普通列表查询 (不分页)
     * 解决报错必须保留这个方法，虽然 Controller 可能暂时不用它了，但接口里定义了，这里就必须有。
     */
    @Override
    public List<DietRecord> listRecords(Long userId, String date) {
        QueryWrapper<DietRecord> query = new QueryWrapper<>();
        query.eq("user_id", userId);

        if (date != null && !date.isEmpty()) {
            query.eq("date", date);
        }

        query.orderByDesc("date");
        List<DietRecord> records = this.list(query);

        // 填充食物详情
        fillFoodInfo(records);

        return records;
    }

    /**
     * 方法 2: 分页查询 (核心逻辑)
     */
    @Override
    public IPage<DietRecord> listRecordsByPage(int pageNum, int pageSize, Long queryUserId, String date) {
        // 1. 构建分页对象
        Page<DietRecord> page = new Page<>(pageNum, pageSize);

        // 2. 构建查询条件
        QueryWrapper<DietRecord> query = new QueryWrapper<>();

        // 如果传了 userId，就查这个人的；如果没传(null)，就查所有人的(管理员模式)
        if (queryUserId != null) {
            query.eq("user_id", queryUserId);
        }

        // 如果传了日期，就过滤日期
        if (date != null && !date.isEmpty()) {
            query.eq("date", date);
        }

        // 倒序排列：先按日期倒序，如果日期一样，按记录ID倒序
        query.orderByDesc("date", "id");

        // 3. 执行分页查询
        this.page(page, query);

        // 4. 填充食物详情 (对当前页的数据进行处理)
        fillFoodInfo(page.getRecords());

        return page;
    }

    /**
     * 辅助私有方法：填充食物名称和热量
     * 避免代码重复，提取出来共用
     */
    private void fillFoodInfo(List<DietRecord> records) {
        for (DietRecord record : records) {
            Food food = foodMapper.selectById(record.getFoodId());
            if (food != null) {
                record.setFoodName(food.getName());
                record.setFoodCalories(food.getCalories());
                record.setTotalCalories(food.getCalories() * record.getQuantity());
            }
        }
    }
}