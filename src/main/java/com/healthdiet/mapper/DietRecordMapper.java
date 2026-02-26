package com.healthdiet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.healthdiet.entity.DietRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DietRecordMapper extends BaseMapper<DietRecord> {
}
