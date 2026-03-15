# Recommendation API Guide

## Scope

This document describes the current recommendation API after the recent backend upgrades:

- whole-day optimization
- activity-level based target calculation
- 7-day intake feedback
- conditional snack generation
- refresh with meal locking
- explanation fields in response

## Endpoints

### 1. Get today's recommendation

- Method: `GET`
- Path: `/api/recommend/today`
- Query:
  - `userId`: user id

Behavior:

- If today's recommendation already exists, the backend returns the cached result in `recommendations.result_json`.
- If not, the backend generates a new day plan and stores it before returning.

### 2. Refresh today's recommendation

- Method: `POST`
- Path: `/api/recommend/refresh`
- Query:
  - `userId`: user id

Behavior:

- Rebuilds today's recommendation.
- If breakfast, lunch, or dinner already has diet records today, those meals are locked and preserved.
- Only meals without intake records are recalculated.

## Response Structure

Both endpoints return the same recommendation payload inside the standard `Result.success(...)` wrapper.

### Root fields

- `date`: recommendation date, format `YYYY-MM-DD`
- `summary`: target-level summary
- `meals`: meal list, now supports `breakfast`, `lunch`, `dinner`, and optional `snack`
- `dailySummary`: whole-day summary
- `extraAdvice`: generic lifestyle tips
- `refreshInfo`: refresh state and locked meal metadata

### `summary`

- `bmi`: current BMI
- `status`: BMI status such as `normal`, `overweight`
- `caloriesTarget`: today's target calories
- `goal`: goal code such as `lose_fat`, `gain_muscle`
- `keyMessage`: short combined recommendation message
- `reasons`: structured explanation list

Typical explanation dimensions:

- BMR and activity-factor basis
- 7-day calorie adjustment
- 7-day macro adjustment
- current goal impact

### `meals[]`

- `type`: `breakfast` | `lunch` | `dinner` | `snack`
- `title`: display title
- `menu`: rendered meal content
- `calories`: meal calories
- `macros.protein`
- `macros.fat`
- `macros.carbs`
- `advice`: short meal suggestion
- `reasons`: why this meal or snack was selected
- `locked`: whether this meal was locked from actual intake records

Notes:

- `snack` is optional and only appears when the system detects a meaningful remaining gap after three meals.
- `locked=true` currently applies to consumed breakfast/lunch/dinner during refresh.

### `dailySummary`

- `totalCalories`
- `totalMacros.protein`
- `totalMacros.fat`
- `totalMacros.carbs`
- `pfcRatio.protein`
- `pfcRatio.fat`
- `pfcRatio.carbs`
- `summaryText`
- `reasons`

This section explains the whole-day view rather than a single meal view.

### `refreshInfo`

- `refreshed`: whether this response came from refresh mode
- `lockedMeals`: locked meal codes, for example `["breakfast"]`
- `message`: short refresh explanation

## Example: normal day plan without snack

```json
{
  "date": "2026-03-16",
  "summary": {
    "bmi": 22.9,
    "status": "normal",
    "caloriesTarget": 1820,
    "goal": "maintain",
    "keyMessage": "Focus: keep the day balanced and sustainable.",
    "reasons": [
      "Target calories are based on BMR, activity factor and user goal.",
      "Current activity factor: 1.35, TDEE about 2210 kcal.",
      "Recent 7-day intake is stable, so no extra calorie correction was applied.",
      "Maintain mode prioritizes day-level balance."
    ]
  },
  "meals": [
    {
      "type": "breakfast",
      "title": "早餐",
      "menu": "燕麦 75g + 鸡蛋 100g + 西兰花 150g",
      "calories": 505,
      "macros": {
        "protein": 31.5,
        "fat": 15.8,
        "carbs": 56.0
      },
      "advice": "Breakfast starts the day with structure and protein support.",
      "reasons": [
        "This meal was selected as part of a whole-day optimization, not in isolation.",
        "Main foods: 燕麦, 鸡蛋, 西兰花.",
        "This meal contributes meaningful fiber to the whole day."
      ],
      "locked": false
    },
    {
      "type": "lunch",
      "title": "午餐",
      "menu": "糙米饭 125g + 鸡胸肉 150g + 生菜 200g",
      "calories": 640,
      "macros": {
        "protein": 43.2,
        "fat": 16.4,
        "carbs": 71.3
      },
      "advice": "Lunch carries the core daytime energy and macro balance.",
      "reasons": [
        "This meal was selected as part of a whole-day optimization, not in isolation.",
        "Main foods: 糙米饭, 鸡胸肉, 生菜."
      ],
      "locked": false
    },
    {
      "type": "dinner",
      "title": "晚餐",
      "menu": "红薯 150g + 鳕鱼 140g + 黄瓜 200g",
      "calories": 598,
      "macros": {
        "protein": 38.6,
        "fat": 17.2,
        "carbs": 74.5
      },
      "advice": "Dinner keeps the day controlled while preserving protein and vegetables.",
      "reasons": [
        "This meal was selected as part of a whole-day optimization, not in isolation.",
        "Main foods: 红薯, 鳕鱼, 黄瓜."
      ],
      "locked": false
    }
  ],
  "dailySummary": {
    "totalCalories": 1743,
    "totalMacros": {
      "protein": 113.3,
      "fat": 49.4,
      "carbs": 201.8
    },
    "pfcRatio": {
      "protein": 0.26,
      "fat": 0.26,
      "carbs": 0.48
    },
    "summaryText": "Execution focus: balanced meals without overcorrection.",
    "reasons": [
      "Day-level scoring considers calories, protein, fat, carbs and fiber together.",
      "Target calories: 1820 kcal, planned calories: 1743 kcal.",
      "Macro target is about P 115.0g / F 55.0g / C 210.0g.",
      "Three meals were enough, so no snack was added.",
      "Cross-meal repetition penalties were applied to avoid a monotonous day plan."
    ]
  },
  "extraAdvice": [
    "Eat slowly to keep satiety more stable.",
    "A short walk after meals helps digestion and glucose control."
  ],
  "refreshInfo": {
    "refreshed": false,
    "lockedMeals": [],
    "message": "Today's recommendation has been generated."
  }
}
```

## Example: refresh after breakfast record exists and snack is added

```json
{
  "date": "2026-03-16",
  "summary": {
    "bmi": 22.9,
    "status": "normal",
    "caloriesTarget": 2100,
    "goal": "gain_muscle",
    "keyMessage": "Protein supply is emphasized today. Focus: enough energy and protein for recovery.",
    "reasons": [
      "Target calories are based on BMR, activity factor and user goal.",
      "Current activity factor: 1.50, TDEE about 2450 kcal.",
      "Recent 7-day intake triggered a smooth calorie adjustment of 120 kcal.",
      "Recent protein intake was low, so protein priority was increased.",
      "Muscle-gain mode prioritizes whole-day energy and recovery support."
    ]
  },
  "meals": [
    {
      "type": "breakfast",
      "title": "早餐",
      "menu": "高蛋白酸奶 200g + 香蕉 100g",
      "calories": 245,
      "macros": {
        "protein": 18.0,
        "fat": 3.8,
        "carbs": 34.0
      },
      "advice": "This meal is locked from today's actual diet record.",
      "reasons": [
        "早餐 already has an intake record today.",
        "Refresh keeps consumed meals and recalculates only later meals."
      ],
      "locked": true
    },
    {
      "type": "lunch",
      "title": "午餐",
      "menu": "糙米饭 150g + 鸡胸肉 160g + 西兰花 200g",
      "calories": 702,
      "macros": {
        "protein": 48.6,
        "fat": 18.2,
        "carbs": 78.0
      },
      "advice": "Lunch carries the core daytime energy and macro balance.",
      "reasons": [
        "This meal was selected as part of a whole-day optimization, not in isolation.",
        "Main foods: 糙米饭, 鸡胸肉, 西兰花."
      ],
      "locked": false
    },
    {
      "type": "dinner",
      "title": "晚餐",
      "menu": "土豆 150g + 三文鱼 150g + 生菜 200g",
      "calories": 760,
      "macros": {
        "protein": 42.0,
        "fat": 24.0,
        "carbs": 62.0
      },
      "advice": "Dinner keeps the day controlled while preserving protein and vegetables.",
      "reasons": [
        "This meal was selected as part of a whole-day optimization, not in isolation.",
        "Main foods: 土豆, 三文鱼, 生菜."
      ],
      "locked": false
    },
    {
      "type": "snack",
      "title": "加餐",
      "menu": "高蛋白酸奶 180g",
      "calories": 162,
      "macros": {
        "protein": 16.2,
        "fat": 3.6,
        "carbs": 14.4
      },
      "advice": "Snack supports recovery and additional energy needs.",
      "reasons": [
        "A snack was added only after checking the remaining whole-day gap.",
        "Protein intake was still below target.",
        "Snack supports whole-day recovery and energy supply."
      ],
      "locked": false
    }
  ],
  "dailySummary": {
    "totalCalories": 1869,
    "totalMacros": {
      "protein": 124.8,
      "fat": 49.6,
      "carbs": 188.4
    },
    "pfcRatio": {
      "protein": 0.27,
      "fat": 0.24,
      "carbs": 0.49
    },
    "summaryText": "Execution focus: enough energy and protein through the day.",
    "reasons": [
      "Day-level scoring considers calories, protein, fat, carbs and fiber together.",
      "Target calories: 2100 kcal, planned calories: 1869 kcal.",
      "Macro target is about P 140.0g / F 60.0g / C 240.0g.",
      "Some meals were locked from actual intake records during refresh.",
      "A controlled snack was added only because the day still had a clear gap.",
      "Cross-meal repetition penalties were applied to avoid a monotonous day plan."
    ]
  },
  "extraAdvice": [
    "Stable sleep helps appetite control and recovery.",
    "Keep hydration steady through the day."
  ],
  "refreshInfo": {
    "refreshed": true,
    "lockedMeals": [
      "breakfast"
    ],
    "message": "Today's recommendation was refreshed while keeping consumed meals locked."
  }
}
```

## Frontend integration notes

- Do not assume there are always exactly 3 meals.
- Render `meals` in returned order and handle optional `snack`.
- Show `locked` visually for refreshed plans.
- Prefer rendering `reasons` directly instead of reconstructing explanation text on the frontend.
- `refreshInfo.lockedMeals` can be used for tags or disabled refresh hints in the UI.

## Current limits

- Locked meal detection currently applies to breakfast, lunch, and dinner records only.
- Recommendation explanation text is already structured, but wording is still backend-generated and not localized by frontend.
- Refresh metadata is currently stored inside response JSON rather than separate database columns.
