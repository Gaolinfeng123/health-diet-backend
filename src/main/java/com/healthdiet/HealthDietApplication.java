package com.healthdiet;

import org.mybatis.spring.annotation.MapperScan; // 1. 注意这里要导入
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.healthdiet.mapper") // 2. 加上这一行！告诉它 Mapper 接口在这个包里
public class HealthDietApplication {

    public static void main(String[] args) {
        SpringApplication.run(HealthDietApplication.class, args);
    }

}
