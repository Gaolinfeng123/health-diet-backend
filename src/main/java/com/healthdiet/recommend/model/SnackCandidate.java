package com.healthdiet.recommend.model;

import com.healthdiet.entity.Food;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SnackCandidate {
    private Food food;
    private int weightG;
    private int calories;
    private double proteinG;
    private double fatG;
    private double carbG;
    private double fiberG;
    private double score;
    private final List<String> reasonTags = new ArrayList<>();

    public void addReasonTag(String tag) {
        if (!reasonTags.contains(tag)) {
            reasonTags.add(tag);
        }
    }
}
