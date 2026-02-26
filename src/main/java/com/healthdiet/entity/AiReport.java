package com.healthdiet.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;

@Data
@TableName("ai_reports")
public class AiReport {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private LocalDate reportDate;
    private String content;
}