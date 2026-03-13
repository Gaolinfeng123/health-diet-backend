package com.healthdiet.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrendPointVO {
    /**
     * 日期，格式：yyyy-MM-dd
     */
    private String date;

    /**
     * 当天总热量
     */
    private Double calories;
}
