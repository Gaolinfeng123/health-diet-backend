package com.healthdiet.recommend.model;

import com.healthdiet.entity.Food;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CandidateBuckets {
    private List<Food> staples = new ArrayList<>();
    private List<Food> proteins = new ArrayList<>();
    private List<Food> veggies = new ArrayList<>();
}
