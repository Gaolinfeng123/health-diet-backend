package com.healthdiet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.healthdiet.entity.Recommendation; // 必须导入实体类
import org.apache.ibatis.annotations.Mapper;

@Mapper
// 【关键点】这里必须是 <Recommendation>，不能是 Double 或 Long
public interface RecommendMapper extends BaseMapper<Recommendation> {
}