package com.healthdiet.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;

@Data
@TableName("recommendations")
public class Recommendation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private LocalDate date;

    // 这里存大段的 JSON 字符串
    private String resultJson;
}