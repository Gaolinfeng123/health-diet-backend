package com.healthdiet.recommend.rule;

import com.healthdiet.entity.Food;
import com.healthdiet.recommend.enums.GoalType;
import com.healthdiet.recommend.enums.MealType;
import com.healthdiet.recommend.enums.VeggieKind;

import java.math.BigDecimal;
import java.util.Locale;

public class FoodRuleHelper {

    private FoodRuleHelper() {
    }

    public static String safeName(Food food) {
        return food == null || food.getName() == null ? "" : food.getName();
    }

    public static String normalizeCategory(Food food) {
        String category = normalizeRawCategory(food == null ? null : food.getFoodCategory());
        if (!category.isEmpty()) {
            return category;
        }

        double protein = safe(food == null ? null : food.getProtein());
        double carb = safe(food == null ? null : food.getCarb());
        double calories = safe(food == null ? null : food.getCalories());
        String name = safeName(food).toLowerCase(Locale.ROOT);

        if (containsAny(name, "milk", "yogurt", "cheese", "soy milk", "牛奶", "酸奶", "奶酪", "豆浆")) {
            return "dairy";
        }
        if (containsAny(name,
                "chicken", "beef", "pork", "fish", "shrimp", "egg", "tofu", "bean",
                "鸡", "牛肉", "猪肉", "鱼", "虾", "蛋", "豆腐", "豆")) {
            return "protein";
        }
        if (containsAny(name,
                "rice", "noodle", "pasta", "oat", "bread", "bagel", "cracker", "corn", "potato", "yam", "quinoa",
                "米饭", "面", "意面", "燕麦", "面包", "贝果", "饼干", "玉米", "土豆", "红薯", "紫薯", "山药", "藜麦", "南瓜", "小米粥", "玉米粥")) {
            return "staple";
        }
        if (containsAny(name,
                "broccoli", "cucumber", "lettuce", "tomato", "spinach", "cabbage", "pepper", "mushroom", "okra",
                "西蓝花", "黄瓜", "生菜", "西红柿", "番茄", "菠菜", "白菜", "娃娃菜", "卷心菜", "青椒", "彩椒", "蘑菇", "香菇", "秋葵", "木耳", "海带")) {
            return "vegetable";
        }

        if (protein >= 10) {
            return "protein";
        }
        if (calories <= 70 && carb <= 15) {
            return "vegetable";
        }
        if (carb >= 15) {
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
        String category = normalizeCategory(food);
        if ("mixed".equals(category)) {
            return true;
        }

        String name = safeName(food).toLowerCase(Locale.ROOT);
        if (containsAny(name,
                "cola", "soda", "milk tea", "burger", "chips", "fries", "pizza", "fried chicken", "beer",
                "可乐", "汽水", "奶茶", "汉堡", "薯片", "炸薯条", "披萨", "炸鸡", "啤酒")) {
            return true;
        }

        return goalType == GoalType.DIABETES_CONTROL
                && "staple".equals(category)
                && "high".equalsIgnoreCase(nullToDefault(food.getGiLevel(), "medium"));
    }

    public static boolean isGoodBreakfastProtein(Food food) {
        String category = normalizeCategory(food);
        String name = safeName(food).toLowerCase(Locale.ROOT);
        if ("dairy".equals(category)) {
            return true;
        }
        return containsAny(name, "egg", "milk", "yogurt", "tofu", "soy", "鸡蛋", "牛奶", "酸奶", "豆腐", "豆浆", "蛋清");
    }

    public static boolean isHeavyBreakfastProtein(Food food) {
        String category = normalizeCategory(food);
        String name = safeName(food).toLowerCase(Locale.ROOT);
        if ("protein".equals(category) && safe(food.getCalories()) >= 180 && proteinDensity(food) >= 12) {
            return true;
        }
        return containsAny(name, "beef", "pork", "salmon", "tuna", "shrimp", "牛肉", "猪肉", "三文鱼", "金枪鱼", "虾");
    }

    public static boolean isNotIdealForHypertension(Food food) {
        if (decimalValue(food.getSodiumMg()) >= 350) {
            return true;
        }
        String name = safeName(food).toLowerCase(Locale.ROOT);
        return containsAny(name, "bacon", "sausage", "ham", "培根", "香肠", "火腿");
    }

    public static VeggieKind getVeggieKind(Food food) {
        if ("vegetable".equals(normalizeCategory(food))) {
            if (safe(food.getCarb()) >= 12 || safe(food.getCalories()) >= 70) {
                return VeggieKind.STARCHY;
            }
            return VeggieKind.LEAFY;
        }

        String name = safeName(food).toLowerCase(Locale.ROOT);
        if (containsAny(name, "potato", "pumpkin", "yam", "corn", "lotus", "sweet potato",
                "土豆", "南瓜", "山药", "玉米", "莲藕", "红薯", "紫薯")) {
            return VeggieKind.STARCHY;
        }
        return VeggieKind.LEAFY;
    }

    public static boolean isLightLeafyVeg(Food food) {
        return "vegetable".equals(normalizeCategory(food))
                && getVeggieKind(food) == VeggieKind.LEAFY
                && safe(food.getCalories()) <= 45
                && fiberValue(food) >= 1.0;
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
        String group = stapleGroup(food);
        if (mealType == MealType.BREAKFAST && "oat_wheat".equals(group)) {
            return 100;
        }
        if ("bakery".equals(group)) {
            return mealType == MealType.BREAKFAST ? 80 : 50;
        }
        if (mealType == MealType.DINNER && profile.getGoalType() == GoalType.LOSE_FAT) {
            return 100;
        }
        if (mealType == MealType.DINNER && profile.getGoalType() == GoalType.DIABETES_CONTROL) {
            return 125;
        }
        if (profile.getGoalType() == GoalType.DIABETES_CONTROL) {
            return "low".equalsIgnoreCase(nullToDefault(food.getGiLevel(), "medium")) ? 180 : 160;
        }
        if (fiberValue(food) >= 6) {
            return 200;
        }
        if (safe(food.getCalories()) >= 180) {
            return 150;
        }
        return 180;
    }

    public static int getProteinMaxWeight(Food food, MealType mealType, GoalRuleProfile profile) {
        String category = normalizeCategory(food);
        if ("dairy".equals(category)) {
            return 300;
        }
        if (isGoodBreakfastProtein(food) && mealType == MealType.BREAKFAST) {
            return 140;
        }
        if (isHeavyBreakfastProtein(food) && mealType == MealType.BREAKFAST) {
            return 120;
        }
        if (profile.getGoalType() == GoalType.GAIN_MUSCLE && proteinDensity(food) >= 12) {
            return 200;
        }
        if (safe(food.getCalories()) >= 220) {
            return 150;
        }
        return 180;
    }

    public static int getVeggieMaxWeight(Food food, MealType mealType, GoalRuleProfile profile) {
        VeggieKind kind = getVeggieKind(food);
        if (kind == VeggieKind.STARCHY) {
            if (mealType == MealType.DINNER) {
                return 150;
            }
            if (profile.getGoalType() == GoalType.LOSE_FAT || profile.getGoalType() == GoalType.DIABETES_CONTROL) {
                return 150;
            }
            return 200;
        }
        if (fiberValue(food) >= 2.5) {
            return 220;
        }
        return 250;
    }

    public static double proteinDensity(Food food) {
        return safe(food.getProtein()) - safe(food.getFat()) * 0.35;
    }

    public static double fiberValue(Food food) {
        return decimalValue(food == null ? null : food.getFiber());
    }

    public static boolean isBreakfastStyleStaple(Food food) {
        if (!"staple".equals(normalizeCategory(food))) {
            return false;
        }
        String group = stapleGroup(food);
        return "oat_wheat".equals(group) || "bakery".equals(group);
    }

    public static boolean isRestrictedMainMealStaple(Food food) {
        if (!"staple".equals(normalizeCategory(food))) {
            return false;
        }

        String group = stapleGroup(food);
        String gi = nullToDefault(food.getGiLevel(), "medium");
        double sodium = decimalValue(food.getSodiumMg());
        double fiber = fiberValue(food);
        String name = safeName(food).toLowerCase(Locale.ROOT);

        if ("oat_wheat".equals(group) || "bakery".equals(group)) {
            return true;
        }
        if ("high".equalsIgnoreCase(gi) && sodium >= 300) {
            return true;
        }
        return fiber < 3 && containsAny(name,
                "bagel", "cracker", "bread", "biscuit",
                "贝果", "饼干", "面包", "苏打");
    }

    public static String proteinSourceGroup(Food food) {
        String category = normalizeCategory(food);
        if ("dairy".equals(category)) {
            return "dairy";
        }

        String name = safeName(food).toLowerCase(Locale.ROOT);
        if (containsAny(name, "egg", "鸡蛋", "蛋清")) {
            return "egg";
        }
        if (containsAny(name, "tofu", "soy", "edamame", "chickpea", "lentil", "black bean", "豆腐", "豆", "毛豆", "鹰嘴豆", "扁豆", "黑豆")) {
            return "soy";
        }
        if (containsAny(name, "fish", "salmon", "shrimp", "tuna", "cod", "sardine", "鱼", "虾", "三文鱼", "金枪鱼", "鳕鱼", "沙丁鱼")) {
            return "seafood";
        }
        if (containsAny(name, "beef", "牛肉")) {
            return "beef";
        }
        if (containsAny(name, "pork", "猪肉")) {
            return "pork";
        }
        if (containsAny(name, "chicken", "turkey", "鸡", "火鸡")) {
            return "poultry";
        }

        if ("protein".equals(category)) {
            if (decimalValue(food.getSaturatedFat()) <= 2 && decimalValue(food.getSodiumMg()) <= 120) {
                return "lean_protein";
            }
            return "protein";
        }
        return category;
    }

    public static String stapleGroup(Food food) {
        if (!"staple".equals(normalizeCategory(food))) {
            return normalizeCategory(food);
        }

        String name = safeName(food).toLowerCase(Locale.ROOT);
        if (containsAny(name, "rice", "porridge", "congee", "米饭", "糙米", "小米粥", "玉米粥")) {
            return "rice";
        }
        if (containsAny(name, "noodle", "pasta", "意面", "面条", "荞麦面", "挂面")) {
            return "noodle";
        }
        if (containsAny(name, "bread", "bagel", "cracker", "biscuit", "toast", "面包", "贝果", "饼干", "苏打")) {
            return "bakery";
        }
        if (containsAny(name, "oat", "wheat", "燕麦", "全麦")) {
            return "oat_wheat";
        }
        if (containsAny(name, "potato", "yam", "pumpkin", "sweet potato", "土豆", "红薯", "紫薯", "山药", "南瓜")) {
            return "potato";
        }
        if (containsAny(name, "corn", "玉米")) {
            return "corn";
        }

        if ("low".equalsIgnoreCase(nullToDefault(food.getGiLevel(), "medium")) && fiberValue(food) >= 5) {
            return "whole_grain";
        }
        if (safe(food.getCalories()) >= 160) {
            return "dense_staple";
        }
        return "staple";
    }

    public static String vegGroup(Food food) {
        if (getVeggieKind(food) == VeggieKind.STARCHY) {
            return "starchy";
        }

        String name = safeName(food).toLowerCase(Locale.ROOT);
        if (containsAny(name, "mushroom", "fungi", "蘑菇", "香菇", "木耳")) {
            return "fungi";
        }
        if (containsAny(name, "tomato", "cucumber", "番茄", "西红柿", "黄瓜")) {
            return "watery";
        }
        if (fiberValue(food) >= 2.0) {
            return "leafy";
        }
        return "watery";
    }

    public static double safe(Double value) {
        return value == null ? 0.0 : value;
    }

    public static double decimalValue(BigDecimal value) {
        return value == null ? 0.0 : value.doubleValue();
    }

    public static String nullToDefault(String value, String defaultValue) {
        return value == null ? defaultValue : value;
    }

    private static String normalizeRawCategory(String rawCategory) {
        if (rawCategory == null) {
            return "";
        }
        String category = rawCategory.trim().toLowerCase(Locale.ROOT);
        return switch (category) {
            case "staple", "carb", "grain", "cereal" -> "staple";
            case "protein", "meat", "egg", "seafood", "bean" -> "protein";
            case "dairy", "milk", "yogurt" -> "dairy";
            case "vegetable", "veg", "veggie" -> "vegetable";
            case "fruit" -> "fruit";
            case "nut", "nuts" -> "nut";
            default -> category;
        };
    }

    private static boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank() && value.contains(candidate.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
