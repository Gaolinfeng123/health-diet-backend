package com.healthdiet.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.healthdiet.entity.DietRecord;
import java.util.List;
import com.baomidou.mybatisplus.core.metadata.IPage;

public interface IDietRecordService extends IService<DietRecord> {
    // 修改：增加 date 参数 (String 类型，格式 YYYY-MM-DD)
    List<DietRecord> listRecords(Long userId, String date);
    IPage<DietRecord> listRecordsByPage(int pageNum, int pageSize, Long userId, String date);
}
