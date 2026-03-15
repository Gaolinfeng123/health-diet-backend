package com.healthdiet.recommend.enums;

public enum ActivityLevel {
    SEDENTARY(1, 1.2),
    LIGHT(2, 1.35),
    MODERATE(3, 1.5),
    HIGH(4, 1.7);

    private final int code;
    private final double factor;

    ActivityLevel(int code, double factor) {
        this.code = code;
        this.factor = factor;
    }

    public int getCode() {
        return code;
    }

    public double getFactor() {
        return factor;
    }

    public static ActivityLevel fromCode(Integer code) {
        if (code == null) {
            return SEDENTARY;
        }
        for (ActivityLevel value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return SEDENTARY;
    }
}
