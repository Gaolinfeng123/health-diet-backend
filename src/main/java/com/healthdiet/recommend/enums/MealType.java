package com.healthdiet.recommend.enums;

public enum MealType {
    BREAKFAST("breakfast", "早餐"),
    LUNCH("lunch", "午餐"),
    DINNER("dinner", "晚餐");

    private final String code;
    private final String title;

    MealType(String code, String title) {
        this.code = code;
        this.title = title;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public static MealType fromCode(String code) {
        for (MealType value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("未知餐次类型: " + code);
    }
}