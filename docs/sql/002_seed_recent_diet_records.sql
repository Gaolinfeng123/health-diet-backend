USE `health_diet`;

-- =========================================================
-- 近 6 天演示饮食记录初始化
-- 适用用户：除管理员外的所有现有用户（17~22）
-- 日期范围：CURDATE() - 6 DAY 到 CURDATE() - 1 DAY
-- 说明：
-- 1. 先清除旧记录，避免前端展示和推荐分析被干扰
-- 2. user_id = 22 在 dump 里 target=-1，但用户名为 keep
--    这里按“keep / 维持型用户画像”生成更适合展示的数据
-- =========================================================

DELETE FROM `diet_records`
WHERE `user_id` IN (17, 18, 19, 20, 21, 22)
  AND `date` BETWEEN CURDATE() - INTERVAL 6 DAY AND CURDATE() - INTERVAL 1 DAY;

-- =========================================================
-- 用户 17: muscle（增肌）
-- 特点：高蛋白、主食充足、训练后有加餐
-- =========================================================

-- day -1
INSERT INTO `diet_records` (`user_id`, `food_id`, `date`, `quantity`, `meal_type`) VALUES
(17, 3,  CURDATE() - INTERVAL 1 DAY, 1, 1),
(17, 51, CURDATE() - INTERVAL 1 DAY, 2, 1),
(17, 49, CURDATE() - INTERVAL 1 DAY, 2, 1),
(17, 20, CURDATE() - INTERVAL 1 DAY, 1, 1),
(17, 1,  CURDATE() - INTERVAL 1 DAY, 2, 2),
(17, 8,  CURDATE() - INTERVAL 1 DAY, 2, 2),
(17, 15, CURDATE() - INTERVAL 1 DAY, 2, 2),
(17, 67, CURDATE() - INTERVAL 1 DAY, 1, 2),
(17, 37, CURDATE() - INTERVAL 1 DAY, 2, 3),
(17, 11, CURDATE() - INTERVAL 1 DAY, 2, 3),
(17, 61, CURDATE() - INTERVAL 1 DAY, 2, 3),
(17, 72, CURDATE() - INTERVAL 1 DAY, 1, 3),
(17, 50, CURDATE() - INTERVAL 1 DAY, 2, 4),
(17, 83, CURDATE() - INTERVAL 1 DAY, 1, 4);

-- day -2
INSERT INTO `diet_records` (`user_id`, `food_id`, `date`, `quantity`, `meal_type`) VALUES
(17, 29, CURDATE() - INTERVAL 2 DAY, 2, 1),
(17, 57, CURDATE() - INTERVAL 2 DAY, 2, 1),
(17, 9,  CURDATE() - INTERVAL 2 DAY, 2, 1),
(17, 28, CURDATE() - INTERVAL 2 DAY, 2, 2),
(17, 44, CURDATE() - INTERVAL 2 DAY, 2, 2),
(17, 65, CURDATE() - INTERVAL 2 DAY, 2, 2),
(17, 68, CURDATE() - INTERVAL 2 DAY, 1, 2),
(17, 6,  CURDATE() - INTERVAL 2 DAY, 2, 3),
(17, 13, CURDATE() - INTERVAL 2 DAY, 2, 3),
(17, 78, CURDATE() - INTERVAL 2 DAY, 2, 3),
(17, 80, CURDATE() - INTERVAL 2 DAY, 1, 3),
(17, 51, CURDATE() - INTERVAL 2 DAY, 1, 4),
(17, 19, CURDATE() - INTERVAL 2 DAY, 1, 4);

-- day -3
INSERT INTO `diet_records` (`user_id`, `food_id`, `date`, `quantity`, `meal_type`) VALUES
(17, 5,  CURDATE() - INTERVAL 3 DAY, 2, 1),
(17, 10, CURDATE() - INTERVAL 3 DAY, 2, 1),
(17, 49, CURDATE() - INTERVAL 3 DAY, 2, 1),
(17, 32, CURDATE() - INTERVAL 3 DAY, 2, 2),
(17, 46, CURDATE() - INTERVAL 3 DAY, 2, 2),
(17, 61, CURDATE() - INTERVAL 3 DAY, 2, 2),
(17, 74, CURDATE() - INTERVAL 3 DAY, 1, 2),
(17, 1,  CURDATE() - INTERVAL 3 DAY, 2, 3),
(17, 43, CURDATE() - INTERVAL 3 DAY, 2, 3),
(17, 75, CURDATE() - INTERVAL 3 DAY, 2, 3),
(17, 69, CURDATE() - INTERVAL 3 DAY, 1, 3),
(17, 50, CURDATE() - INTERVAL 3 DAY, 2, 4),
(17, 20, CURDATE() - INTERVAL 3 DAY, 1, 4);

-- day -4
INSERT INTO `diet_records` (`user_id`, `food_id`, `date`, `quantity`, `meal_type`) VALUES
(17, 3,  CURDATE() - INTERVAL 4 DAY, 1, 1),
(17, 57, CURDATE() - INTERVAL 4 DAY, 2, 1),
(17, 9,  CURDATE() - INTERVAL 4 DAY, 2, 1),
(17, 28, CURDATE() - INTERVAL 4 DAY, 2, 2),
(17, 47, CURDATE() - INTERVAL 4 DAY, 2, 2),
(17, 63, CURDATE() - INTERVAL 4 DAY, 2, 2),
(17, 81, CURDATE() - INTERVAL 4 DAY, 1, 2),
(17, 31, CURDATE() - INTERVAL 4 DAY, 2, 3),
(17, 8,  CURDATE() - INTERVAL 4 DAY, 2, 3),
(17, 77, CURDATE() - INTERVAL 4 DAY, 2, 3),
(17, 72, CURDATE() - INTERVAL 4 DAY, 1, 3),
(17, 51, CURDATE() - INTERVAL 4 DAY, 1, 4),
(17, 85, CURDATE() - INTERVAL 4 DAY, 1, 4);

-- day -5
INSERT INTO `diet_records` (`user_id`, `food_id`, `date`, `quantity`, `meal_type`) VALUES
(17, 29, CURDATE() - INTERVAL 5 DAY, 2, 1),
(17, 10, CURDATE() - INTERVAL 5 DAY, 2, 1),
(17, 49, CURDATE() - INTERVAL 5 DAY, 2, 1),
(17, 1,  CURDATE() - INTERVAL 5 DAY, 2, 2),
(17, 11, CURDATE() - INTERVAL 5 DAY, 2, 2),
(17, 15, CURDATE() - INTERVAL 5 DAY, 2, 2),
(17, 67, CURDATE() - INTERVAL 5 DAY, 1, 2),
(17, 37, CURDATE() - INTERVAL 5 DAY, 2, 3),
(17, 45, CURDATE() - INTERVAL 5 DAY, 2, 3),
(17, 61, CURDATE() - INTERVAL 5 DAY, 2, 3),
(17, 79, CURDATE() - INTERVAL 5 DAY, 1, 3),
(17, 50, CURDATE() - INTERVAL 5 DAY, 2, 4),
(17, 84, CURDATE() - INTERVAL 5 DAY, 1, 4);

-- day -6
INSERT INTO `diet_records` (`user_id`, `food_id`, `date`, `quantity`, `meal_type`) VALUES
(17, 5,  CURDATE() - INTERVAL 6 DAY, 2, 1),
(17, 57, CURDATE() - INTERVAL 6 DAY, 2, 1),
(17, 9,  CURDATE() - INTERVAL 6 DAY, 2, 1),
(17, 32, CURDATE() - INTERVAL 6 DAY, 2, 2),
(17, 8,  CURDATE() - INTERVAL 6 DAY, 2, 2),
(17, 66, CURDATE() - INTERVAL 6 DAY, 2, 2),
(17, 68, CURDATE() - INTERVAL 6 DAY, 1, 2),
(17, 6,  CURDATE() - INTERVAL 6 DAY, 2, 3),
(17, 13, CURDATE() - INTERVAL 6 DAY, 2, 3),
(17, 75, CURDATE() - INTERVAL 6 DAY, 2, 3),
(17, 73, CURDATE() - INTERVAL 6 DAY, 1, 3),
(17, 51, CURDATE() - INTERVAL 6 DAY, 1, 4),
(17, 83, CURDATE() - INTERVAL 6 DAY, 1, 4);

-- =========================================================
-- 用户 18: fater（减脂）
-- 特点：控总热量、提高蛋白、晚餐更轻
-- =========================================================

-- day -1
INSERT INTO `diet_records` (`user_id`, `food_id`, `date`, `quantity`, `meal_type`) VALUES
(18, 3,  CURDATE() - INTERVAL 1 DAY, 1, 1),
(18, 50, CURDATE() - INTERVAL 1 DAY, 1, 1),
(18, 49, CURDATE() - INTERVAL 1 DAY, 2, 1),
(18, 28, CURDATE() - INTERVAL 1 DAY, 1, 2),
(18, 8,  CURDATE() - INTERVAL 1 DAY, 1, 2),
(18, 15, CURDATE() - INTERVAL 1 DAY, 2, 2),
(18, 18, CURDATE() - INTERVAL 1 DAY, 1, 2),
(18, 14, CURDATE() - INTERVAL 1 DAY, 2, 3),
(18, 61, CURDATE() - INTERVAL 1 DAY, 2, 3),
(18, 72, CURDATE() - INTERVAL 1 DAY, 1, 3),
(18, 85, CURDATE() - INTERVAL 1 DAY, 1, 4);

-- day -2
INSERT INTO `diet_records` (`user_id`, `food_id`, `date`, `quantity`, `meal_type`) VALUES
(18, 29, CURDATE() - INTERVAL 2 DAY, 1, 1),
(18, 57, CURDATE() - INTERVAL 2 DAY, 2, 1),
(18, 9,  CURDATE() - INTERVAL 2 DAY, 1, 1),
(18, 6,  CURDATE() - INTERVAL 2 DAY, 1, 2),
(18, 45, CURDATE() - INTERVAL 2 DAY, 1, 2),
(18, 75, CURDATE() - INTERVAL 2 DAY, 2, 2),
(18, 28, CURDATE() - INTERVAL 2 DAY, 1, 3),
(18, 53, CURDATE() - INTERVAL 2 DAY, 1, 3),
(18, 63, CURDATE() - INTERVAL 2 DAY, 2, 3),
(18, 17, CURDATE() - INTERVAL 2 DAY, 1, 3),
(18, 83, CURDATE() - INTERVAL 2 DAY, 1, 4);

-- day -3
INSERT INTO `diet_records` (`user_id`, `food_id`, `date`, `quantity`, `meal_type`) VALUES
(18, 3,  CURDATE() - INTERVAL 3 DAY, 1, 1),
(18, 10, CURDATE() - INTERVAL 3 DAY, 1, 1),
(18, 86, CURDATE() - INTERVAL 3 DAY, 1, 1),
(18, 31, CURDATE() - INTERVAL 3 DAY, 1, 2),
(18, 46, CURDATE() - INTERVAL 3 DAY, 1, 2),
(18, 61, CURDATE() - INTERVAL 3 DAY, 2, 2),
(18, 74, CURDATE() - INTERVAL 3 DAY, 1, 2),
(18, 14, CURDATE() - INTERVAL 3 DAY, 2, 3),
(18, 16, CURDATE() - INTERVAL 3 DAY, 2, 3),
(18, 18, CURDATE() - INTERVAL 3 DAY, 1, 3),
(18, 50, CURDATE() - INTERVAL 3 DAY, 1, 4);

-- day -4
INSERT INTO `diet_records` (`user_id`, `food_id`, `date`, `quantity`, `meal_type`) VALUES
(18, 29, CURDATE() - INTERVAL 4 DAY, 1, 1),
(18, 50, CURDATE() - INTERVAL 4 DAY, 1, 1),
(18, 49, CURDATE() - INTERVAL 4 DAY, 2, 1),
(18, 28, CURDATE() - INTERVAL 4 DAY, 1, 2),
(18, 44, CURDATE() - INTERVAL 4 DAY, 1, 2),
(18, 65, CURDATE() - INTERVAL 4 DAY, 2, 2),
(18, 68, CURDATE() - INTERVAL 4 DAY, 1, 2),
(18, 6,  CURDATE() - INTERVAL 4 DAY, 1, 3),
(18, 14, CURDATE() - INTERVAL 4 DAY, 2, 3),
(18, 78, CURDATE() - INTERVAL 4 DAY, 2, 3),
(18, 84, CURDATE() - INTERVAL 4 DAY, 1, 4);

-- day -5
INSERT INTO `diet_records` (`user_id`, `food_id`, `date`, `quantity`, `meal_type`) VALUES
(18, 3,  CURDATE() - INTERVAL 5 DAY, 1, 1),
(18, 57, CURDATE() - INTERVAL 5 DAY, 2, 1),
(18, 85, CURDATE() - INTERVAL 5 DAY, 1, 1),
(18, 35, CURDATE() - INTERVAL 5 DAY, 1, 2),
(18, 8,  CURDATE() - INTERVAL 5 DAY, 1, 2),
(18, 62, CURDATE() - INTERVAL 5 DAY, 2, 2),
(18, 14, CURDATE() - INTERVAL 5 DAY, 2, 3),
(18, 15, CURDATE() - INTERVAL 5 DAY, 2, 3),
(18, 17, CURDATE() - INTERVAL 5 DAY, 1, 3),
(18, 83, CURDATE() - INTERVAL 5 DAY, 1, 4);

-- day -6
INSERT INTO `diet_records` (`user_id`, `food_id`, `date`, `quantity`, `meal_type`) VALUES
(18, 29, CURDATE() - INTERVAL 6 DAY, 1, 1),
(18, 10, CURDATE() - INTERVAL 6 DAY, 1, 1),
(18, 9,  CURDATE() - INTERVAL 6 DAY, 1, 1),
(18, 6,  CURDATE() - INTERVAL 6 DAY, 1, 2),
(18, 45, CURDATE() - INTERVAL 6 DAY, 1, 2),
(18, 61, CURDATE() - INTERVAL 6 DAY, 2, 2),
(18, 71, CURDATE() - INTERVAL 6 DAY, 1, 2),
(18, 53, CURDATE() - INTERVAL 6 DAY, 1, 3),
(18, 63, CURDATE() - INTERVAL 6 DAY, 2, 3),
(18, 18, CURDATE() - INTERVAL 6 DAY, 1, 3),
(18, 50, CURDATE() - INTERVAL 6 DAY, 1, 4);

-- =========================================================
-- 用户 19: diabetes（控糖）
-- 特点：低 GI 主食、稳定碳水、高纤维、低糖加餐
-- =========================================================

-- day -1
INSERT INTO `diet_records` (`user_id`, `food_id`, `date`, `quantity`, `meal_type`) VALUES
(19, 3,  CURDATE() - INTERVAL 1 DAY, 1, 1),
(19, 57, CURDATE() - INTERVAL 1 DAY, 2, 1),
(19, 9,  CURDATE() - INTERVAL 1 DAY, 1, 1),
(19, 32, CURDATE() - INTERVAL 1 DAY, 2, 2),
(19, 14, CURDATE() - INTERVAL 1 DAY, 2, 2),
(19, 78, CURDATE() - INTERVAL 1 DAY, 2, 2),
(19, 81, CURDATE() - INTERVAL 1 DAY, 1, 2),
(19, 33, CURDATE() - INTERVAL 1 DAY, 2, 3),
(19, 45, CURDATE() - INTERVAL 1 DAY, 1, 3),
(19, 61, CURDATE() - INTERVAL 1 DAY, 2, 3),
(19, 50, CURDATE() - INTERVAL 1 DAY, 1, 4),
(19, 86, CURDATE() - INTERVAL 1 DAY, 1, 4);

-- day -2
INSERT INTO `diet_records` (`user_id`, `food_id`, `date`, `quantity`, `meal_type`) VALUES
(19, 29, CURDATE() - INTERVAL 2 DAY, 2, 1),
(19, 10, CURDATE() - INTERVAL 2 DAY, 1, 1),
(19, 49, CURDATE() - INTERVAL 2 DAY, 2, 1),
(19, 5,  CURDATE() - INTERVAL 2 DAY, 2, 2),
(19, 53, CURDATE() - INTERVAL 2 DAY, 1, 2),
(19, 81, CURDATE() - INTERVAL 2 DAY, 2, 2),
(19, 6,  CURDATE() - INTERVAL 2 DAY, 2, 3),
(19, 14, CURDATE() - INTERVAL 2 DAY, 2, 3),
(19, 76, CURDATE() - INTERVAL 2 DAY, 1, 3),
(19, 51, CURDATE() - INTERVAL 2 DAY, 1, 4);

-- day -3
INSERT INTO `diet_records` (`user_id`, `food_id`, `date`, `quantity`, `meal_type`) VALUES
(19, 3,  CURDATE() - INTERVAL 3 DAY, 1, 1),
(19, 57, CURDATE() - INTERVAL 3 DAY, 2, 1),
(19, 85, CURDATE() - INTERVAL 3 DAY, 1, 1),
(19, 32, CURDATE() - INTERVAL 3 DAY, 2, 2),
(19, 45, CURDATE() - INTERVAL 3 DAY, 1, 2),
(19, 15, CURDATE() - INTERVAL 3 DAY, 2, 2),
(19, 78, CURDATE() - INTERVAL 3 DAY, 1, 2),
(19, 31, CURDATE() - INTERVAL 3 DAY, 2, 3),
(19, 14, CURDATE() - INTERVAL 3 DAY, 2, 3),
(19, 61, CURDATE() - INTERVAL 3 DAY, 2, 3),
(19, 50, CURDATE() - INTERVAL 3 DAY, 1, 4),
(19, 83, CURDATE() - INTERVAL 3 DAY, 1, 4);

-- day -4
INSERT INTO `diet_records` (`user_id`, `food_id`, `date`, `quantity`, `meal_type`) VALUES
(19, 29, CURDATE() - INTERVAL 4 DAY, 2, 1),
(19, 57, CURDATE() - INTERVAL 4 DAY, 2, 1),
(19, 49, CURDATE() - INTERVAL 4 DAY, 2, 1),
(19, 28, CURDATE() - INTERVAL 4 DAY, 2, 2),
(19, 8,  CURDATE() - INTERVAL 4 DAY, 1, 2),
(19, 15, CURDATE() - INTERVAL 4 DAY, 2, 2),
(19, 81, CURDATE() - INTERVAL 4 DAY, 1, 2),
(19, 33, CURDATE() - INTERVAL 4 DAY, 2, 3),
(19, 14, CURDATE() - INTERVAL 4 DAY, 2, 3),
(19, 78, CURDATE() - INTERVAL 4 DAY, 2, 3),
(19, 51, CURDATE() - INTERVAL 4 DAY, 1, 4);

-- day -5
INSERT INTO `diet_records` (`user_id`, `food_id`, `date`, `quantity`, `meal_type`) VALUES
(19, 3,  CURDATE() - INTERVAL 5 DAY, 1, 1),
(19, 10, CURDATE() - INTERVAL 5 DAY, 1, 1),
(19, 9,  CURDATE() - INTERVAL 5 DAY, 1, 1),
(19, 32, CURDATE() - INTERVAL 5 DAY, 2, 2),
(19, 53, CURDATE() - INTERVAL 5 DAY, 1, 2),
(19, 76, CURDATE() - INTERVAL 5 DAY, 1, 2),
(19, 61, CURDATE() - INTERVAL 5 DAY, 2, 2),
(19, 6,  CURDATE() - INTERVAL 5 DAY, 2, 3),
(19, 45, CURDATE() - INTERVAL 5 DAY, 1, 3),
(19, 80, CURDATE() - INTERVAL 5 DAY, 1, 3),
(19, 50, CURDATE() - INTERVAL 5 DAY, 1, 4),
(19, 86, CURDATE() - INTERVAL 5 DAY, 1, 4);

-- day -6
INSERT INTO `diet_records` (`user_id`, `food_id`, `date`, `quantity`, `meal_type`) VALUES
(19, 29, CURDATE() - INTERVAL 6 DAY, 2, 1),
(19, 57, CURDATE() - INTERVAL 6 DAY, 2, 1),
(19, 49, CURDATE() - INTERVAL 6 DAY, 2, 1),
(19, 5,  CURDATE() - INTERVAL 6 DAY, 2, 2),
(19, 14, CURDATE() - INTERVAL 6 DAY, 2, 2),
(19, 78, CURDATE() - INTERVAL 6 DAY, 2, 2),
(19, 33, CURDATE() - INTERVAL 6 DAY, 2, 3),
(19, 8,  CURDATE() - INTERVAL 6 DAY, 1, 3),
(19, 61, CURDATE() - INTERVAL 6 DAY, 2, 3),
(19, 81, CURDATE() - INTERVAL 6 DAY, 1, 3),
(19, 51, CURDATE() - INTERVAL 6 DAY, 1, 4);

-- =========================================================
-- 用户 20: Hyperlipidemia（高脂血症）
-- 特点：降低饱和脂肪，增加鱼类、豆类和膳食纤维
-- =========================================================

-- day -1
INSERT INTO `diet_records` (`user_id`, `food_id`, `date`, `quantity`, `meal_type`) VALUES
(20, 3,  CURDATE() - INTERVAL 1 DAY, 1, 1),
(20, 57, CURDATE() - INTERVAL 1 DAY, 2, 1),
(20, 85, CURDATE() - INTERVAL 1 DAY, 1, 1),
(20, 32, CURDATE() - INTERVAL 1 DAY, 2, 2),
(20, 45, CURDATE() - INTERVAL 1 DAY, 1, 2),
(20, 15, CURDATE() - INTERVAL 1 DAY, 2, 2),
(20, 81, CURDATE() - INTERVAL 1 DAY, 1, 2),
(20, 6,  CURDATE() - INTERVAL 1 DAY, 2, 3),
(20, 14, CURDATE() - INTERVAL 1 DAY, 2, 3),
(20, 61, CURDATE() - INTERVAL 1 DAY, 2, 3),
(20, 50, CURDATE() - INTERVAL 1 DAY, 1, 4),
(20, 83, CURDATE() - INTERVAL 1 DAY, 1, 4);

-- day -2
INSERT INTO `diet_records` (`user_id`, `food_id`, `date`, `quantity`, `meal_type`) VALUES
(20, 29, CURDATE() - INTERVAL 2 DAY, 2, 1),
(20, 10, CURDATE() - INTERVAL 2 DAY, 1, 1),
(20, 19, CURDATE() - INTERVAL 2 DAY, 1, 1),
(20, 28, CURDATE() - INTERVAL 2 DAY, 2, 2),
(20, 46, CURDATE() - INTERVAL 2 DAY, 1, 2),
(20, 65, CURDATE() - INTERVAL 2 DAY, 2, 2),
(20, 68, CURDATE() - INTERVAL 2 DAY, 1, 2),
(20, 31, CURDATE() - INTERVAL 2 DAY, 2, 3),
(20, 53, CURDATE() - INTERVAL 2 DAY, 1, 3),
(20, 71, CURDATE() - INTERVAL 2 DAY, 2, 3),
(20, 72, CURDATE() - INTERVAL 2 DAY, 1, 3),
(20, 85, CURDATE() - INTERVAL 2 DAY, 1, 4);

-- day -3
INSERT INTO `diet_records` (`user_id`, `food_id`, `date`, `quantity`, `meal_type`) VALUES
(20, 3,  CURDATE() - INTERVAL 3 DAY, 1, 1),
(20, 50, CURDATE() - INTERVAL 3 DAY, 1, 1),
(20, 86, CURDATE() - INTERVAL 3 DAY, 1, 1),
(20, 32, CURDATE() - INTERVAL 3 DAY, 2, 2),
(20, 47, CURDATE() - INTERVAL 3 DAY, 1, 2),
(20, 61, CURDATE() - INTERVAL 3 DAY, 2, 2),
(20, 74, CURDATE() - INTERVAL 3 DAY, 1, 2),
(20, 33, CURDATE() - INTERVAL 3 DAY, 2, 3),
(20, 14, CURDATE() - INTERVAL 3 DAY, 2, 3),
(20, 78, CURDATE() - INTERVAL 3 DAY, 2, 3),
(20, 50, CURDATE() - INTERVAL 3 DAY, 1, 4),
(20, 84, CURDATE() - INTERVAL 3 DAY, 1, 4);

-- day -4
INSERT INTO `diet_records` (`user_id`, `food_id`, `date`, `quantity`, `meal_type`) VALUES
(20, 29, CURDATE() - INTERVAL 4 DAY, 2, 1),
(20, 57, CURDATE() - INTERVAL 4 DAY, 2, 1),
(20, 83, CURDATE() - INTERVAL 4 DAY, 1, 1),
(20, 28, CURDATE() - INTERVAL 4 DAY, 2, 2),
(20, 45, CURDATE() - INTERVAL 4 DAY, 1, 2),
(20, 75, CURDATE() - INTERVAL 4 DAY, 2, 2),
(20, 69, CURDATE() - INTERVAL 4 DAY, 1, 2),
(20, 6,  CURDATE() - INTERVAL 4 DAY, 2, 3),
(20, 53, CURDATE() - INTERVAL 4 DAY, 1, 3),
(20, 63, CURDATE() - INTERVAL 4 DAY, 2, 3),
(20, 72, CURDATE() - INTERVAL 4 DAY, 1, 3),
(20, 85, CURDATE() - INTERVAL 4 DAY, 1, 4);

-- day -5
INSERT INTO `diet_records` (`user_id`, `food_id`, `date`, `quantity`, `meal_type`) VALUES
(20, 3,  CURDATE() - INTERVAL 5 DAY, 1, 1),
(20, 57, CURDATE() - INTERVAL 5 DAY, 2, 1),
(20, 19, CURDATE() - INTERVAL 5 DAY, 1, 1),
(20, 32, CURDATE() - INTERVAL 5 DAY, 2, 2),
(20, 46, CURDATE() - INTERVAL 5 DAY, 1, 2),
(20, 61, CURDATE() - INTERVAL 5 DAY, 2, 2),
(20, 81, CURDATE() - INTERVAL 5 DAY, 1, 2),
(20, 31, CURDATE() - INTERVAL 5 DAY, 2, 3),
(20, 14, CURDATE() - INTERVAL 5 DAY, 2, 3),
(20, 80, CURDATE() - INTERVAL 5 DAY, 1, 3),
(20, 86, CURDATE() - INTERVAL 5 DAY, 1, 4);

-- day -6
INSERT INTO `diet_records` (`user_id`, `food_id`, `date`, `quantity`, `meal_type`) VALUES
(20, 29, CURDATE() - INTERVAL 6 DAY, 2, 1),
(20, 10, CURDATE() - INTERVAL 6 DAY, 1, 1),
(20, 85, CURDATE() - INTERVAL 6 DAY, 1, 1),
(20, 28, CURDATE() - INTERVAL 6 DAY, 2, 2),
(20, 47, CURDATE() - INTERVAL 6 DAY, 1, 2),
(20, 65, CURDATE() - INTERVAL 6 DAY, 2, 2),
(20, 67, CURDATE() - INTERVAL 6 DAY, 1, 2),
(20, 33, CURDATE() - INTERVAL 6 DAY, 2, 3),
(20, 14, CURDATE() - INTERVAL 6 DAY, 2, 3),
(20, 78, CURDATE() - INTERVAL 6 DAY, 2, 3),
(20, 50, CURDATE() - INTERVAL 6 DAY, 1, 4),
(20, 83, CURDATE() - INTERVAL 6 DAY, 1, 4);

-- =========================================================
-- 用户 21: Hypertension（高血压）
-- 特点：低钠、少加工、清淡稳定
-- =========================================================

-- day -1
INSERT INTO `diet_records` (`user_id`, `food_id`, `date`, `quantity`, `meal_type`) VALUES
(21, 29, CURDATE() - INTERVAL 1 DAY, 2, 1),
(21, 57, CURDATE() - INTERVAL 1 DAY, 2, 1),
(21, 9,  CURDATE() - INTERVAL 1 DAY, 1, 1),
(21, 1,  CURDATE() - INTERVAL 1 DAY, 2, 2),
(21, 45, CURDATE() - INTERVAL 1 DAY, 1, 2),
(21, 61, CURDATE() - INTERVAL 1 DAY, 2, 2),
(21, 74, CURDATE() - INTERVAL 1 DAY, 1, 2),
(21, 6,  CURDATE() - INTERVAL 1 DAY, 2, 3),
(21, 14, CURDATE() - INTERVAL 1 DAY, 2, 3),
(21, 63, CURDATE() - INTERVAL 1 DAY, 2, 3),
(21, 84, CURDATE() - INTERVAL 1 DAY, 1, 4);

-- day -2
INSERT INTO `diet_records` (`user_id`, `food_id`, `date`, `quantity`, `meal_type`) VALUES
(21, 3,  CURDATE() - INTERVAL 2 DAY, 1, 1),
(21, 10, CURDATE() - INTERVAL 2 DAY, 1, 1),
(21, 86, CURDATE() - INTERVAL 2 DAY, 1, 1),
(21, 28, CURDATE() - INTERVAL 2 DAY, 2, 2),
(21, 8,  CURDATE() - INTERVAL 2 DAY, 1, 2),
(21, 15, CURDATE() - INTERVAL 2 DAY, 2, 2),
(21, 81, CURDATE() - INTERVAL 2 DAY, 1, 2),
(21, 33, CURDATE() - INTERVAL 2 DAY, 2, 3),
(21, 45, CURDATE() - INTERVAL 2 DAY, 1, 3),
(21, 62, CURDATE() - INTERVAL 2 DAY, 2, 3),
(21, 50, CURDATE() - INTERVAL 2 DAY, 1, 4);

-- day -3
INSERT INTO `diet_records` (`user_id`, `food_id`, `date`, `quantity`, `meal_type`) VALUES
(21, 5,  CURDATE() - INTERVAL 3 DAY, 2, 1),
(21, 57, CURDATE() - INTERVAL 3 DAY, 2, 1),
(21, 49, CURDATE() - INTERVAL 3 DAY, 2, 1),
(21, 1,  CURDATE() - INTERVAL 3 DAY, 2, 2),
(21, 44, CURDATE() - INTERVAL 3 DAY, 1, 2),
(21, 65, CURDATE() - INTERVAL 3 DAY, 2, 2),
(21, 68, CURDATE() - INTERVAL 3 DAY, 1, 2),
(21, 6,  CURDATE() - INTERVAL 3 DAY, 2, 3),
(21, 14, CURDATE() - INTERVAL 3 DAY, 2, 3),
(21, 77, CURDATE() - INTERVAL 3 DAY, 2, 3),
(21, 83, CURDATE() - INTERVAL 3 DAY, 1, 4);

-- day -4
INSERT INTO `diet_records` (`user_id`, `food_id`, `date`, `quantity`, `meal_type`) VALUES
(21, 29, CURDATE() - INTERVAL 4 DAY, 2, 1),
(21, 10, CURDATE() - INTERVAL 4 DAY, 1, 1),
(21, 17, CURDATE() - INTERVAL 4 DAY, 1, 1),
(21, 28, CURDATE() - INTERVAL 4 DAY, 2, 2),
(21, 45, CURDATE() - INTERVAL 4 DAY, 1, 2),
(21, 75, CURDATE() - INTERVAL 4 DAY, 2, 2),
(21, 74, CURDATE() - INTERVAL 4 DAY, 1, 2),
(21, 33, CURDATE() - INTERVAL 4 DAY, 2, 3),
(21, 14, CURDATE() - INTERVAL 4 DAY, 2, 3),
(21, 16, CURDATE() - INTERVAL 4 DAY, 2, 3),
(21, 50, CURDATE() - INTERVAL 4 DAY, 1, 4),
(21, 86, CURDATE() - INTERVAL 4 DAY, 1, 4);

-- day -5
INSERT INTO `diet_records` (`user_id`, `food_id`, `date`, `quantity`, `meal_type`) VALUES
(21, 3,  CURDATE() - INTERVAL 5 DAY, 1, 1),
(21, 57, CURDATE() - INTERVAL 5 DAY, 2, 1),
(21, 9,  CURDATE() - INTERVAL 5 DAY, 1, 1),
(21, 1,  CURDATE() - INTERVAL 5 DAY, 2, 2),
(21, 8,  CURDATE() - INTERVAL 5 DAY, 1, 2),
(21, 61, CURDATE() - INTERVAL 5 DAY, 2, 2),
(21, 72, CURDATE() - INTERVAL 5 DAY, 1, 2),
(21, 6,  CURDATE() - INTERVAL 5 DAY, 2, 3),
(21, 45, CURDATE() - INTERVAL 5 DAY, 1, 3),
(21, 63, CURDATE() - INTERVAL 5 DAY, 2, 3),
(21, 84, CURDATE() - INTERVAL 5 DAY, 1, 4);

-- day -6
INSERT INTO `diet_records` (`user_id`, `food_id`, `date`, `quantity`, `meal_type`) VALUES
(21, 29, CURDATE() - INTERVAL 6 DAY, 2, 1),
(21, 10, CURDATE() - INTERVAL 6 DAY, 1, 1),
(21, 85, CURDATE() - INTERVAL 6 DAY, 1, 1),
(21, 28, CURDATE() - INTERVAL 6 DAY, 2, 2),
(21, 44, CURDATE() - INTERVAL 6 DAY, 1, 2),
(21, 15, CURDATE() - INTERVAL 6 DAY, 2, 2),
(21, 74, CURDATE() - INTERVAL 6 DAY, 1, 2),
(21, 33, CURDATE() - INTERVAL 6 DAY, 2, 3),
(21, 14, CURDATE() - INTERVAL 6 DAY, 2, 3),
(21, 78, CURDATE() - INTERVAL 6 DAY, 2, 3),
(21, 50, CURDATE() - INTERVAL 6 DAY, 1, 4),
(21, 83, CURDATE() - INTERVAL 6 DAY, 1, 4);

-- =========================================================
-- 用户 22: keep（维持）
-- 说明：虽然 dump 里 target=-1，但这里按 keep 用户画像生成
-- 特点：均衡饮食、主食稳定、三餐完整、少量加餐
-- =========================================================

-- day -1
INSERT INTO `diet_records` (`user_id`, `food_id`, `date`, `quantity`, `meal_type`) VALUES
(22, 29, CURDATE() - INTERVAL 1 DAY, 2, 1),
(22, 10, CURDATE() - INTERVAL 1 DAY, 1, 1),
(22, 9,  CURDATE() - INTERVAL 1 DAY, 1, 1),
(22, 1,  CURDATE() - INTERVAL 1 DAY, 2, 2),
(22, 8,  CURDATE() - INTERVAL 1 DAY, 1, 2),
(22, 15, CURDATE() - INTERVAL 1 DAY, 2, 2),
(22, 67, CURDATE() - INTERVAL 1 DAY, 1, 2),
(22, 28, CURDATE() - INTERVAL 1 DAY, 2, 3),
(22, 13, CURDATE() - INTERVAL 1 DAY, 1, 3),
(22, 61, CURDATE() - INTERVAL 1 DAY, 2, 3),
(22, 72, CURDATE() - INTERVAL 1 DAY, 1, 3),
(22, 83, CURDATE() - INTERVAL 1 DAY, 1, 4);

-- day -2
INSERT INTO `diet_records` (`user_id`, `food_id`, `date`, `quantity`, `meal_type`) VALUES
(22, 5,  CURDATE() - INTERVAL 2 DAY, 2, 1),
(22, 57, CURDATE() - INTERVAL 2 DAY, 2, 1),
(22, 49, CURDATE() - INTERVAL 2 DAY, 2, 1),
(22, 28, CURDATE() - INTERVAL 2 DAY, 2, 2),
(22, 43, CURDATE() - INTERVAL 2 DAY, 1, 2),
(22, 65, CURDATE() - INTERVAL 2 DAY, 2, 2),
(22, 68, CURDATE() - INTERVAL 2 DAY, 1, 2),
(22, 6,  CURDATE() - INTERVAL 2 DAY, 2, 3),
(22, 14, CURDATE() - INTERVAL 2 DAY, 2, 3),
(22, 78, CURDATE() - INTERVAL 2 DAY, 2, 3),
(22, 85, CURDATE() - INTERVAL 2 DAY, 1, 4);

-- day -3
INSERT INTO `diet_records` (`user_id`, `food_id`, `date`, `quantity`, `meal_type`) VALUES
(22, 3,  CURDATE() - INTERVAL 3 DAY, 1, 1),
(22, 50, CURDATE() - INTERVAL 3 DAY, 1, 1),
(22, 84, CURDATE() - INTERVAL 3 DAY, 1, 1),
(22, 1,  CURDATE() - INTERVAL 3 DAY, 2, 2),
(22, 11, CURDATE() - INTERVAL 3 DAY, 2, 2),
(22, 75, CURDATE() - INTERVAL 3 DAY, 2, 2),
(22, 31, CURDATE() - INTERVAL 3 DAY, 2, 3),
(22, 45, CURDATE() - INTERVAL 3 DAY, 1, 3),
(22, 71, CURDATE() - INTERVAL 3 DAY, 2, 3),
(22, 79, CURDATE() - INTERVAL 3 DAY, 1, 3),
(22, 19, CURDATE() - INTERVAL 3 DAY, 1, 4);

-- day -4
INSERT INTO `diet_records` (`user_id`, `food_id`, `date`, `quantity`, `meal_type`) VALUES
(22, 30, CURDATE() - INTERVAL 4 DAY, 2, 1),
(22, 10, CURDATE() - INTERVAL 4 DAY, 1, 1),
(22, 9,  CURDATE() - INTERVAL 4 DAY, 1, 1),
(22, 28, CURDATE() - INTERVAL 4 DAY, 2, 2),
(22, 46, CURDATE() - INTERVAL 4 DAY, 1, 2),
(22, 61, CURDATE() - INTERVAL 4 DAY, 2, 2),
(22, 74, CURDATE() - INTERVAL 4 DAY, 1, 2),
(22, 33, CURDATE() - INTERVAL 4 DAY, 2, 3),
(22, 12, CURDATE() - INTERVAL 4 DAY, 1, 3),
(22, 63, CURDATE() - INTERVAL 4 DAY, 2, 3),
(22, 72, CURDATE() - INTERVAL 4 DAY, 1, 3),
(22, 50, CURDATE() - INTERVAL 4 DAY, 1, 4),
(22, 83, CURDATE() - INTERVAL 4 DAY, 1, 4);

-- day -5
INSERT INTO `diet_records` (`user_id`, `food_id`, `date`, `quantity`, `meal_type`) VALUES
(22, 29, CURDATE() - INTERVAL 5 DAY, 2, 1),
(22, 57, CURDATE() - INTERVAL 5 DAY, 2, 1),
(22, 49, CURDATE() - INTERVAL 5 DAY, 2, 1),
(22, 1,  CURDATE() - INTERVAL 5 DAY, 2, 2),
(22, 47, CURDATE() - INTERVAL 5 DAY, 1, 2),
(22, 66, CURDATE() - INTERVAL 5 DAY, 2, 2),
(22, 67, CURDATE() - INTERVAL 5 DAY, 1, 2),
(22, 6,  CURDATE() - INTERVAL 5 DAY, 2, 3),
(22, 14, CURDATE() - INTERVAL 5 DAY, 2, 3),
(22, 73, CURDATE() - INTERVAL 5 DAY, 2, 3),
(22, 86, CURDATE() - INTERVAL 5 DAY, 1, 4);

-- day -6
INSERT INTO `diet_records` (`user_id`, `food_id`, `date`, `quantity`, `meal_type`) VALUES
(22, 5,  CURDATE() - INTERVAL 6 DAY, 2, 1),
(22, 10, CURDATE() - INTERVAL 6 DAY, 1, 1),
(22, 9,  CURDATE() - INTERVAL 6 DAY, 1, 1),
(22, 37, CURDATE() - INTERVAL 6 DAY, 2, 2),
(22, 44, CURDATE() - INTERVAL 6 DAY, 1, 2),
(22, 65, CURDATE() - INTERVAL 6 DAY, 2, 2),
(22, 68, CURDATE() - INTERVAL 6 DAY, 1, 2),
(22, 28, CURDATE() - INTERVAL 6 DAY, 2, 3),
(22, 60, CURDATE() - INTERVAL 6 DAY, 1, 3),
(22, 75, CURDATE() - INTERVAL 6 DAY, 2, 3),
(22, 80, CURDATE() - INTERVAL 6 DAY, 1, 3),
(22, 85, CURDATE() - INTERVAL 6 DAY, 1, 4);
