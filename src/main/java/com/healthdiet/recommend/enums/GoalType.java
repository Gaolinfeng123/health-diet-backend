package com.healthdiet.recommend.enums;

public enum GoalType {
    LOSE_FAT(-1),
    MAINTAIN(0),
    GAIN_MUSCLE(1),
    DIABETES_CONTROL(2),
    HYPERTENSION_CONTROL(3),
    HYPERLIPIDEMIA_CONTROL(4);

    private final int code;

    GoalType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static GoalType fromCode(Integer code) {
        if (code == null) {
            return MAINTAIN;
        }
        for (GoalType value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return MAINTAIN;
    }
}