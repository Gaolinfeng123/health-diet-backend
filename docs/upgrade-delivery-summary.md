# Upgrade Delivery Summary

## Delivered items

The backend recommendation system now includes these completed upgrades:

1. Whole-day optimization instead of isolated per-meal optimization
2. Activity-level based calorie target calculation
3. Smoothed 7-day intake feedback with macro-direction adjustment
4. Conditional snack generation after three-meal planning
5. Stronger cross-meal repetition control
6. More structured-field-driven food classification
7. Manual refresh for today's recommendation
8. Explanation fields in recommendation response
9. Improved candidate truncation and lightweight beam-style search

## Main backend changes

### Shared target calculation

- Added unified daily target service for recommendation and analysis
- Added activity-level support to the user model
- Added SQL migration:
  - `docs/sql/001_add_activity_level.sql`

### Recommendation engine

- Upgraded meal generation from single best meal to top-N meal candidates
- Added whole-day scorer to select the best breakfast/lunch/dinner combination
- Added conditional snack planning
- Added candidate diversification and beam-style search

### Refresh and explainability

- Added `POST /api/recommend/refresh`
- Preserves already consumed breakfast/lunch/dinner via meal locking
- Added explanation fields:
  - target-level reasons
  - meal-level reasons
  - whole-day reasons
  - refresh metadata

## Main files

- Recommendation service:
  - `src/main/java/com/healthdiet/service/impl/RecommendServiceImpl.java`
- Recommendation response:
  - `src/main/java/com/healthdiet/entity/vo/RecommendVO.java`
- Recommendation controller:
  - `src/main/java/com/healthdiet/controller/RecommendController.java`
- Whole-day scorer:
  - `src/main/java/com/healthdiet/recommend/rule/DayPlanScorer.java`
- Snack planner:
  - `src/main/java/com/healthdiet/recommend/rule/SnackPlanner.java`
- Food rule helper:
  - `src/main/java/com/healthdiet/recommend/rule/FoodRuleHelper.java`
- API guide:
  - `docs/recommend-api-guide.md`
- Upgrade checklist:
  - `docs/system-upgrade-checklist.md`

## Test coverage added

Current targeted tests cover:

- daily target calculation
- food rule helper behavior
- whole-day scoring
- snack generation
- refresh meal locking
- candidate diversification
- beam-search daily selection

Test files:

- `src/test/java/com/healthdiet/service/DailyTargetServiceTest.java`
- `src/test/java/com/healthdiet/recommend/rule/FoodRuleHelperTest.java`
- `src/test/java/com/healthdiet/recommend/rule/DayPlanScorerTest.java`
- `src/test/java/com/healthdiet/recommend/rule/SnackPlannerTest.java`
- `src/test/java/com/healthdiet/service/RecommendServiceImplTest.java`

## Current API shape

Recommendation response now supports:

- `summary.reasons`
- `meals[].reasons`
- `meals[].locked`
- optional `snack` meal
- `dailySummary.reasons`
- `refreshInfo`

## Known remaining gaps

These are not blocked, but still good future work:

1. Replace remaining controller/service user-facing English fallback text with final product language if needed
2. Add integration-level API tests instead of only service-level recommendation tests
3. Add explicit structured food fields such as `protein_source_type`, `staple_type`, `veg_type` if data volume grows
4. Decide whether refresh metadata should also be stored in dedicated database columns
5. Add frontend rendering updates for optional snack and locked meals if not already done

## Recommended next handoff

For frontend or demo integration, start from:

1. `docs/recommend-api-guide.md`
2. `docs/upgrade-delivery-summary.md`
3. `docs/system-upgrade-checklist.md`
