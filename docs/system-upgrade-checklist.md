# Health Diet Backend System Upgrade Checklist

## Scope

This checklist is based on the current backend implementation and focuses on turning the existing recommendation flow into a more complete day-level diet planning system.

## Current Architecture Summary

After reading the current code, the recommendation module has these characteristics:

- Daily recommendation is generated on first request and then cached by date in `recommendations.result_json`.
- The current generation path is `RecommendController -> RecommendServiceImpl#getTodayRecommend -> generateSmartPlan`.
- Breakfast, lunch, and dinner are solved in sequence. Cross-meal linkage only relies on `usedFoodIds`, which adds a simple duplicate penalty.
- Daily calories are calculated from `BMR * 1.3`, plus a crude 7-day average calorie adjustment, then a goal-based fixed offset.
- The same target calorie logic is duplicated in recommendation and analysis.
- `foods` already contains structured fields such as `food_category`, `gi_level`, `sodium_mg`, `saturated_fat`, `fiber`, `sugar`, `breakfast_friendly`, and `dinner_friendly`.
- Even so, many classification and scoring rules still depend on `food.name` keyword matching.
- `diet_records.meal_type` already supports snack (`4`), and analysis already tracks `snackCal`, but recommendation only outputs three meals.
- There are no meaningful recommendation tests yet. The test suite currently only contains `contextLoads`.

## Code Evidence

- Sequential meal solving: `src/main/java/com/healthdiet/service/impl/RecommendServiceImpl.java`
- Candidate truncation and per-meal brute force: `src/main/java/com/healthdiet/service/impl/RecommendServiceImpl.java`
- Duplicate penalty only uses food id: `src/main/java/com/healthdiet/recommend/rule/RecommendationScorer.java`
- Heavy name-based fallback logic: `src/main/java/com/healthdiet/recommend/rule/FoodRuleHelper.java`
- Fixed activity factor and simple 7-day feedback: `src/main/java/com/healthdiet/service/impl/RecommendServiceImpl.java`
- Analysis side duplicates target calculation and already includes snack fields: `src/main/java/com/healthdiet/service/impl/AnalysisServiceImpl.java`
- User model currently has no activity level field: `src/main/java/com/healthdiet/entity/User.java`
- Recommendation API only exposes `/api/recommend/today`: `src/main/java/com/healthdiet/controller/RecommendController.java`
- Recommendation response currently has no explanation structure: `src/main/java/com/healthdiet/entity/vo/RecommendVO.java`

## Cross-Cutting Refactors To Do First

These are not extra business requirements, but they should be treated as prerequisites because several upgrade items depend on them.

### 1. Extract daily target calculation into one shared service

Current issue:

- `RecommendServiceImpl` and `AnalysisServiceImpl` both compute BMR, TDEE, target calories, and macro targets separately.
- If activity level and 7-day feedback are upgraded in only one place, recommendation and analysis will drift.

Recommended refactor:

- Add a shared domain service such as `DailyTargetService` or `NutritionTargetService`.
- Input: user profile, goal type, date, history snapshot.
- Output: `DailyTargetProfile` containing:
  - target calories
  - target protein/fat/carb
  - activity factor
  - history adjustment summary
  - decision tags for explanation

Direct beneficiaries:

- Upgrade item 2: activity level
- Upgrade item 3: 7-day feedback
- Upgrade item 8: explainability

### 2. Add recommendation-focused tests before large algorithm upgrades

Current issue:

- The project has no regression safety net for algorithm changes.

Recommended test layers:

- Unit test: `FoodRuleHelper`
- Unit test: meal scoring
- Unit test: daily scoring
- Unit test: history feedback analyzer
- Service test: recommendation generation with a fixed food dataset
- Integration test: recommendation refresh API

Minimum acceptance:

- A fixed input dataset should produce deterministic output.
- Day-level scoring changes should be assertable without database randomness.

## Upgrade Checklist

### 1. Upgrade from "per-meal optimal" to "whole-day optimal"

Priority: High

### Current state

- `generateSmartPlan` solves breakfast, lunch, and dinner one by one.
- `composeSmartMeal` returns only one best meal result per meal type.
- `solveBestPortion` performs exhaustive search inside one meal, not across the whole day.
- `buildDailySummary` only summarizes already chosen meals; it does not participate in optimization.

### Upgrade objective

- Optimize breakfast, lunch, and dinner as one day-level combination instead of three isolated local optima.

### Recommended implementation

Use a two-stage optimizer that keeps the current meal-level logic but adds a day-level combination layer.

Stage 1: keep top N meal candidates per meal

- Replace `composeSmartMeal` with `generateMealCandidates`.
- Keep the existing food bucketing and portion search logic.
- Instead of returning one `MealBuildResult`, return top N scored candidates.
- Suggested initial values:
  - breakfast top 10
  - lunch top 10
  - dinner top 10

Stage 2: combine meal candidates into a day plan

- Add a day-level model such as:
  - `DayPlanCandidate`
  - `DayPlanScore`
  - `DailyTargetProfile`
- Combine breakfast/lunch/dinner candidate sets and compute a day score.
- Day score should include:
  - total calorie deviation
  - total protein deviation
  - total fat deviation
  - total carb deviation
  - fiber reward
  - repeated food penalty
  - repeated food-type penalty
  - goal-specific penalties such as dinner carb overload for fat loss or high-GI load for diabetes control

Suggested class split:

- Keep `RecommendationScorer` for single-meal scoring or rename it to `MealScorer`.
- Add `DayPlanScorer` for whole-day scoring.

### Why this fits the current project

- Current meal search logic is already usable and does not need to be thrown away.
- Candidate count remains small. `10 * 10 * 10 = 1000` day combinations is acceptable.
- This gives a clear quality upgrade without immediately moving to a much more complex optimizer.

### Files and models involved

- `src/main/java/com/healthdiet/service/impl/RecommendServiceImpl.java`
- `src/main/java/com/healthdiet/recommend/model/*`
- `src/main/java/com/healthdiet/recommend/rule/RecommendationScorer.java`
- `src/main/java/com/healthdiet/entity/vo/RecommendVO.java`

### Acceptance criteria

- Whole-day score is part of the selection process, not only post-hoc summary.
- Generated day plan is allowed to choose a slightly weaker breakfast if it unlocks a better overall day.

### 2. Add activity level tiers

Priority: High

### Current state

- `User` has no activity-level field.
- TDEE currently uses a single fixed factor from `configService.getDouble("BMR_ACTIVITY_FACTOR", 1.3)`.
- `ConfigService` currently always returns the default value, so this is effectively hard-coded.

### Upgrade objective

- Allow calorie targets to reflect sedentary, lightly active, moderately active, and highly active users.

### Recommended implementation

Data model:

- Add `activityLevel` to `users`.
- Suggested codes:
  - `1`: sedentary
  - `2`: light
  - `3`: moderate
  - `4`: high
- Set a safe default for old users, preferably `1`.

API updates:

- Add `activityLevel` to:
  - `User`
  - `RegisterDTO`
  - user profile update flow
  - admin add/update flow if admins can edit user health profile

Algorithm mapping:

- Use a small internal map or enum:
  - sedentary: `1.2`
  - light: `1.35`
  - moderate: `1.5`
  - high: `1.7`
- Replace the current fixed TDEE factor with `BMR * activityFactor`.

Better design than current config usage:

- Do not rely on `ConfigService` for this first step.
- Prefer an enum or explicit mapper so the factor is deterministic and testable.

### Files involved

- `src/main/java/com/healthdiet/entity/User.java`
- `src/main/java/com/healthdiet/entity/dto/RegisterDTO.java`
- `src/main/java/com/healthdiet/service/impl/UserServiceImpl.java`
- `src/main/java/com/healthdiet/controller/UserController.java`
- `src/main/java/com/healthdiet/controller/AdminController.java`
- shared target calculation service after refactor

### Acceptance criteria

- Recommendation and analysis use the same activity factor for the same user.
- Editing activity level can affect the next generated recommendation.

### 3. Upgrade 7-day intake feedback logic

Priority: High

### Current state

- Recommendation only looks at average calories across the last 7 days.
- If average intake is `> TDEE + 500`, it subtracts 300 kcal.
- If average intake is `< TDEE - 500`, it adds 200 kcal.
- Macro structure is not part of the feedback logic.

### Upgrade objective

- Make the system respond to recent eating patterns more smoothly and more intelligently.

### Recommended implementation

Extract a dedicated history analyzer, for example `HistoryIntakeAnalyzer`.

Step 1: energy deviation

- Keep average calorie comparison, but switch to a weighted average:
  - recent 3 days higher weight
  - previous 4 days lower weight

Step 2: macro deviation

- Compute rolling averages for:
  - protein
  - fat
  - carbs
  - optional fiber
- Produce structured corrections such as:
  - `calorieAdjustment`
  - `proteinAdjustment`
  - `fatAdjustment`
  - `carbAdjustment`
  - `fiberPriorityBoost`

Step 3: clamp correction range

- Suggested limits:
  - calorie correction: `[-300, +300]`
  - macro ratio correction: small ratio shifts only

Suggested behavior examples:

- Calories normal but protein consistently low:
  - slightly raise protein target
- Calories slightly high and fat consistently high:
  - tighten fat target before making large calorie cuts

### Important implementation detail

- The current `analyzeHistoryIntake` only aggregates calories.
- Upgrade it to return a richer structure, not just one double.

### Files involved

- shared target calculation service
- `src/main/java/com/healthdiet/service/impl/RecommendServiceImpl.java`
- `src/main/java/com/healthdiet/service/impl/AnalysisServiceImpl.java`
- possibly new VO/DTO for explanation output

### Acceptance criteria

- One abnormal day does not cause extreme target swings.
- History feedback can adjust both calories and macro emphasis.

### 4. Make snack generation conditional instead of fixed

Priority: Medium

### Current state

- `DietRecord` already supports snack (`mealType = 4`).
- `AnalysisReport` already records `snackCal`.
- Recommendation side has only three meals:
  - `MealType` enum has breakfast/lunch/dinner only
  - `RecommendVO` has no snack section

### Upgrade objective

- Let the system decide whether snack is needed after three meals are generated.

### Recommended implementation

Generation order:

1. Generate day-level breakfast/lunch/dinner first.
2. Evaluate remaining gaps against the whole-day target.
3. Only generate snack if rules are triggered.

Suggested snack triggers:

- total calories still clearly below target
- protein still below threshold
- fiber still below threshold
- gain-muscle goal needs extra protein/energy support
- diabetes-control goal benefits from smaller meal distribution

Snack pool:

- Reuse current `foods` data.
- Use categories such as:
  - `fruit`
  - `nut`
  - selected protein foods suitable for snack

Minimal implementation suggestion:

- Add `MealType.SNACK` in recommendation layer.
- Add snack candidate buckets separate from regular meals.
- Keep snack constraints stricter:
  - calorie upper bound
  - serving upper bound
  - stricter night-time rules if snack is treated as evening snack

### Files involved

- `src/main/java/com/healthdiet/entity/DietRecord.java`
- `src/main/java/com/healthdiet/recommend/enums/MealType.java`
- `src/main/java/com/healthdiet/entity/vo/RecommendVO.java`
- recommendation service and scorer

### Acceptance criteria

- Snack is optional, not mandatory.
- Three-meal plan remains valid when snack is not needed.

### 5. Improve cross-meal repetition control and linkage

Priority: Medium

### Current state

- The current system only uses `usedFoodIds` to penalize repeated exact food ids.
- There is no explicit control over repeated protein source, staple style, or vegetable type.

### Upgrade objective

- Make meals feel like one coherent day plan rather than three isolated meal templates.

### Recommended implementation

Add two levels of linkage.

Level 1: exact food repetition

- Keep the current `usedFoodIds` penalty.
- Tune weights after introducing day-level scoring.

Level 2: food-type repetition

- Add lightweight derived grouping for:
  - protein source type
  - staple type
  - vegetable type
- Examples:
  - breakfast egg, lunch chicken breast, dinner fish
  - avoid same staple style all day

Suggested first-step grouping strategy:

- First use structured fields where possible.
- Then use centralized fallback classification in one helper class.
- Avoid scattering new name-based grouping logic across multiple scorers.

Possible helper outputs:

- `proteinSourceGroup`
- `stapleGroup`
- `vegGroup`

### Files involved

- `src/main/java/com/healthdiet/recommend/rule/FoodRuleHelper.java`
- recommendation scorer and day scorer

### Acceptance criteria

- Day plan scoring can penalize repeated food ids and repeated food types separately.

### 6. Reduce reliance on food name and prefer structured fields

Priority: High

### Current state

- `foods` already has useful structured fields.
- But `FoodRuleHelper` still uses many `name.contains(...)` rules for:
  - category fallback
  - exclusion rules
  - breakfast protein detection
  - heavy breakfast protein detection
  - hypertension heuristics
  - starchy vegetable detection
  - serving upper bounds

### Upgrade objective

- Make recommendation behavior depend primarily on stable data fields instead of naming conventions.

### Recommended implementation

Unify rule priority:

1. structured fields first
2. derived structured classification second
3. name keywords only as fallback

Recommended approach:

- Keep `FoodRuleHelper` as the single fallback entry point.
- Split helper responsibilities clearly:
  - category normalization
  - meal suitability fallback
  - source grouping fallback
  - special-food detection fallback

Practical first batch:

- Use `foodCategory` as primary category, not optional hint.
- Use `giLevel`, `sodiumMg`, `saturatedFat`, `fiber`, `sugar`, `breakfastFriendly`, and `dinnerFriendly` as primary inputs for scoring.
- Keep keyword rules only when the required structured field is missing.

Optional schema evolution if needed later:

- `protein_source_type`
- `staple_type`
- `veg_type`
- `snack_friendly`

This is optional, not required for the first upgrade batch.

### Files involved

- `src/main/java/com/healthdiet/entity/Food.java`
- `src/main/java/com/healthdiet/recommend/rule/FoodRuleHelper.java`
- `src/main/java/com/healthdiet/service/impl/RecommendServiceImpl.java`
- `src/main/java/com/healthdiet/recommend/rule/RecommendationScorer.java`

### Acceptance criteria

- The same food with a renamed display name should still be classified correctly if structured fields are present.

### 7. Support "refresh today's recommendation"

Priority: High

### Current state

- `getTodayRecommend` returns the existing recommendation immediately if one already exists for the day.
- There is no API for forced regeneration.
- `recommendations` only stores `resultJson`, with no refresh mode or locked-meal metadata.

### Upgrade objective

- Allow manual refresh and condition-based refresh while preserving already eaten meals when needed.

### Recommended implementation

Add two refresh modes.

Mode 1: manual refresh

- Add a new endpoint such as `POST /api/recommend/refresh`.
- Allow users to actively request another plan for the same day.

Mode 2: conditional refresh

- Trigger refresh eligibility when key inputs change:
  - body weight
  - goal
  - activity level
  - newly added diet records

Meal locking strategy:

- If the user already has diet records for breakfast, lock breakfast and only recalculate later meals.
- Minimal rule:
  - any `diet_records` entry for that day and `mealType` means the meal is considered consumed

Persistence strategy:

- Either store refresh metadata in new columns, or store it inside `resultJson`.
- Recommended metadata:
  - `generatedAt`
  - `refreshType`
  - `lockedMealTypes`
  - `sourceVersion` or `profileSnapshot`

### Files involved

- `src/main/java/com/healthdiet/controller/RecommendController.java`
- `src/main/java/com/healthdiet/service/IRecommendService.java`
- `src/main/java/com/healthdiet/service/impl/RecommendServiceImpl.java`
- `src/main/java/com/healthdiet/entity/Recommendation.java`
- `src/main/java/com/healthdiet/entity/DietRecord.java`

### Acceptance criteria

- Force refresh is possible.
- Partial refresh can preserve already consumed meals.

### 8. Strengthen recommendation explainability

Priority: Medium-High

### Current state

- Response has summary text, meal advice, and extra advice, but these are mostly static templates.
- Explanation is not tied to actual algorithm decisions.

### Upgrade objective

- Return both recommendation result and why the system made that choice.

### Recommended implementation

Add decision-tag recording during calculation.

Suggested tag categories:

- target-level tags
  - `ACTIVITY_LIGHT`
  - `HISTORY_CAL_DOWN`
  - `HISTORY_PROTEIN_UP`
- goal-level tags
  - `LOW_GI_SELECTED`
  - `DINNER_CARB_REDUCED`
  - `LOW_SODIUM_PRIORITY`
- meal-level tags
  - `BREAKFAST_LIGHT_PROTEIN`
  - `FIBER_BOOSTED`
  - `REPEAT_PENALTY_APPLIED`
- snack tags
  - `SNACK_NOT_NEEDED`
  - `SNACK_FOR_PROTEIN_GAP`

Response design suggestion:

- Extend `RecommendVO` with explanation blocks instead of only plain strings.
- Example additions:
  - day-level explanation list
  - meal-level reason list
  - raw tags if front end needs them for rendering or demo

Natural-language generation strategy:

- Backend should generate tags during optimization.
- A final formatter converts tags into readable Chinese text.
- Do not let front end guess the reasoning from the result.

### Files involved

- shared target calculation service
- `src/main/java/com/healthdiet/entity/vo/RecommendVO.java`
- `src/main/java/com/healthdiet/service/impl/RecommendServiceImpl.java`
- scorer classes

### Acceptance criteria

- Explanation text changes when the underlying algorithm decision changes.
- At least four explanation dimensions are covered:
  - calorie basis
  - goal impact
  - meal structure reason
  - snack reason

### 9. Improve the search strategy after candidate truncation

Priority: Medium

### Current state

- Candidate buckets are truncated early:
  - staples top 10
  - proteins top 10
  - veggies top 12
- Ranking is mostly single-food oriented.
- Good combination-friendly foods may be removed too early.

### Upgrade objective

- Improve search quality without replacing the current architecture with a much heavier solver.

### Recommended implementation

Layer 1: widen candidate retention moderately

- Try a safer initial expansion such as:
  - staple 15
  - protein 15
  - veggie 15

Layer 2: diversify candidate ranking

- Add simple diversity protection:
  - keep top K globally
  - also reserve quota by derived food group
- This avoids the bucket being filled with near-identical items.

Layer 3: smarter search over candidate space

- After day-level optimization is in place, consider:
  - beam search
  - layered top-K retention
  - partial combination pruning

Recommended phased approach:

- First ship the two-stage day optimizer with wider candidate buckets.
- Then add beam search only if recommendation quality still plateaus or runtime becomes an issue.

### Files involved

- `src/main/java/com/healthdiet/service/impl/RecommendServiceImpl.java`
- `src/main/java/com/healthdiet/recommend/model/CandidateBuckets.java`
- scorer classes

### Acceptance criteria

- Candidate expansion improves day score quality without unacceptable latency growth.

## Suggested Implementation Order

### Sprint 1: foundation

- Extract shared daily target calculation
- Add activity level
- Upgrade 7-day feedback logic
- Reduce food-name dependency
- Add tests

### Sprint 2: core recommendation experience

- Upgrade to whole-day optimal
- Add refresh-today-recommend support
- Add explainability tags and response structure

### Sprint 3: quality and completeness

- Add conditional snack generation
- Improve cross-meal repetition linkage
- Upgrade candidate truncation and search strategy

## Minimal Database/API Change List

Database changes likely needed:

- `users.activity_level`
- optional recommendation metadata columns if refresh state should be queryable without parsing JSON

API changes likely needed:

- user register/update: activity level
- admin user add/update: activity level
- recommendation refresh endpoint
- recommendation response explanation fields
- optional snack section in recommendation response

## Recommended New Backend Components

To keep the project maintainable, these new components are worth introducing instead of continuing to pile logic into `RecommendServiceImpl`.

- `DailyTargetService`
- `HistoryIntakeAnalyzer`
- `MealCandidateGenerator`
- `DayPlanScorer`
- `RecommendationExplanationBuilder`
- `FoodClassificationHelper` or a stricter `FoodRuleHelper` split

## Final Conclusion

This project already has a usable recommendation core, but it is still closer to a meal-level rule engine than a real day-level planner.

The highest-value upgrade path is:

1. unify target calculation
2. add activity level and richer 7-day feedback
3. move from single-meal optimum to whole-day optimum
4. add refresh and explanation so the system becomes interactive and demonstrable

That route gives the best balance between implementation cost, output quality, and demo value.
