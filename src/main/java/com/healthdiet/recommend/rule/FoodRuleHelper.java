package com.healthdiet.recommend.rule;

import com.healthdiet.entity.Food;
import com.healthdiet.recommend.enums.GoalType;
import com.healthdiet.recommend.enums.MealType;
import com.healthdiet.recommend.enums.VeggieKind;

public class FoodRuleHelper {

    private FoodRuleHelper() {}

    public static String safeName(Food food) {
        return food == null || food.getName() == null ? "" : food.getName();
    }

    public static String normalizeCategory(Food food) {
        String category = food.getFoodCategory() == null ? "" : food.getFoodCategory().trim().toLowerCase();
        if (!category.isEmpty()) {
            return category;
        }

        String name = safeName(food);
        if (safe(food.getProtein()) > 10 || name.contains("肉") || name.contains("蛋") || name.contains("鱼") || name.contains("豆腐")) {
            return "protein";
        }
        if (safe(food.getCalories()) < 60 && safe(food.getCarb()) < 15) {
            return "vegetable";
        }
        if (safe(food.getCarb()) > 15 || name.contains("饭") || name.contains("面") || name.contains("粥")) {
            return "staple";
        }
        return "mixed";
    }

    public static boolean isMealFriendly(Food food, MealType mealType) {
        if (mealType == MealType.BREAKFAST) {
            return food.getBreakfastFriendly() == null || food.getBreakfastFriendly() == 1;
        }
        if (mealType == MealType.DINNER) {
            return food.getDinnerFriendly() == null || food.getDinnerFriendly() == 1;
        }
        return true;
    }

    public static boolean shouldExclude(Food food, GoalType goalType) {
        String name = safeName(food);
        String category = normalizeCategory(food);

        if ("mixed".equals(category) || "fruit".equals(category) || "nut".equals(category)) {
            return true;
        }

        if (name.contains("可乐")
                || name.contains("奶茶")
                || name.contains("巧克力")
                || name.contains("汉堡")
                || name.contains("薯片")
                || name.contains("炸薯条")
                || name.contains("披萨")
                || name.contains("炸鸡")
                || name.contains("啤酒")
                || name.contains("苹果汁")
                || name.contains("橙汁")
                || name.contains("白面包")
                || name.contains("苏打饼干")) {
            return true;
        }

        if (goalType == GoalType.DIABETES_CONTROL
                && "high".equalsIgnoreCase(nullToDefault(food.getGiLevel(), "medium"))
                && "staple".equals(category)) {
            return true;
        }

        return false;
    }

    public static boolean isGoodBreakfastProtein(Food food) {
        String name = safeName(food);
        return name.contains("鸡蛋")
                || name.contains("鸡蛋清")
                || name.contains("牛奶")
                || name.contains("酸奶")
                || name.contains("豆浆")
                || name.contains("豆腐");
    }

    public static boolean isHeavyBreakfastProtein(Food food) {
        String name = safeName(food);
        return name.contains("鸡胸")
                || name.contains("猪肉")
                || name.contains("牛肉")
                || name.contains("三文鱼")
                || name.contains("沙丁鱼")
                || name.contains("鳕鱼")
                || name.contains("虾仁")
                || name.contains("金枪鱼")
                || name.contains("鸡腿")
                || name.contains("鸭胸");
    }

    public static boolean isNotIdealForHypertension(Food food) {
        String name = safeName(food);
        return name.contains("猪肉")
                || name.contains("牛肉丸")
                || name.contains("鱼豆腐")
                || name.contains("沙丁鱼");
    }

    public static VeggieKind getVeggieKind(Food food) {
        String name = safeName(food);
        if (name.contains("莲藕")
                || name.contains("南瓜")
                || name.contains("山药")
                || name.contains("土豆")
                || name.contains("紫薯")) {
            return VeggieKind.STARCHY;
        }
        return VeggieKind.LEAFY;
    }

    public static boolean isLightLeafyVeg(Food food) {
        String name = safeName(food);
        return name.contains("西蓝花")
                || name.contains("生菜")
                || name.contains("黄瓜")
                || name.contains("西红柿")
                || name.contains("菠菜")
                || name.contains("白菜")
                || name.contains("油麦菜")
                || name.contains("卷心菜")
                || name.contains("花椰菜")
                || name.contains("苦瓜")
                || name.contains("秋葵");
    }

    public static boolean isReasonableCombination(
            GoalRuleProfile profile,
            MealType mealType,
            Food staple, int stapleWeight,
            Food veggie, int veggieWeight
    ) {
        VeggieKind kind = getVeggieKind(veggie);

        if (profile.isRestrictStarchyVegAtDinner() && mealType == MealType.DINNER && kind == VeggieKind.STARCHY) {
            if (stapleWeight >= 100 && veggieWeight >= 200) {
                return false;
            }
        }

        if (profile.getGoalType() == GoalType.LOSE_FAT && mealType == MealType.DINNER && kind == VeggieKind.STARCHY) {
            return stapleWeight <= 75;
        }

        if (profile.getGoalType() == GoalType.DIABETES_CONTROL && kind == VeggieKind.STARCHY) {
            return !(stapleWeight >= 150 && veggieWeight >= 200);
        }

        return true;
    }

    public static int getStapleMaxWeight(Food food, MealType mealType, GoalRuleProfile profile) {
        String name = safeName(food);

        if (name.contains("燕麦")) return 80;
        if (name.contains("面包")) return 100;
        if (name.contains("馒头")) return 100;
        if (name.contains("玉米")) return 150;
        if (name.contains("红薯")) return 180;
        if (name.contains("藜麦")) return 175;
        if (name.contains("粥")) return 200;

        if (mealType == MealType.DINNER && profile.getGoalType() == GoalType.LOSE_FAT) return 100;
        if (mealType == MealType.DINNER && profile.getGoalType() == GoalType.DIABETES_CONTROL) return 125;
        if (profile.getGoalType() == GoalType.DIABETES_CONTROL) return 160;
        return 180;
    }

    public static int getProteinMaxWeight(Food food, MealType mealType, GoalRuleProfile profile) {
        String name = safeName(food);

        if (name.contains("鸡蛋")) return 120;
        if (name.contains("牛奶")) return 300;
        if (name.contains("酸奶")) return 250;
        if (name.contains("豆浆")) return 300;
        if (name.contains("豆腐")) return 220;
        if (name.contains("三文鱼")) return 160;
        if (name.contains("沙丁鱼")) return 150;
        if (name.contains("毛豆")) return mealType == MealType.BREAKFAST ? 120 : 180;
        if (mealType == MealType.BREAKFAST && isHeavyBreakfastProtein(food)) return 120;
        if (profile.getGoalType() == GoalType.GAIN_MUSCLE) return 200;
        return 180;
    }

    public static int getVeggieMaxWeight(Food food, MealType mealType, GoalRuleProfile profile) {
        VeggieKind kind = getVeggieKind(food);
        String name = safeName(food);

        if (kind == VeggieKind.STARCHY) {
            if (mealType == MealType.DINNER) return 150;
            if (profile.getGoalType() == GoalType.LOSE_FAT || profile.getGoalType() == GoalType.DIABETES_CONTROL) return 150;
            return 200;
        }

        if (name.contains("木耳")) return 200;
        if (name.contains("番茄") || name.contains("西红柿")) return 200;
        if (name.contains("黄瓜")) return 200;
        return 250;
    }

    public static double proteinDensity(Food food) {
        return safe(food.getProtein()) - safe(food.getFat()) * 0.35;
    }

    public static double fiberValue(Food food) {
        return food.getFiber() == null ? 0.0 : food.getFiber().doubleValue();
    }

    public static double safe(Double value) {
        return value == null ? 0.0 : value;
    }

    public static double decimalValue(java.math.BigDecimal value) {
        return value == null ? 0.0 : value.doubleValue();
    }

    public static String nullToDefault(String value, String defaultValue) {
        return value == null ? defaultValue : value;
    }
}
