# Contributing to cloud-itonami-isic-4781

Contributions should preserve the actor's scope: stall/market food,
beverage and tobacco retail back-office coordination only, with CRITICAL
exclusions of food-safety-clearance finalization, age-verification-
override finalization (tobacco sales), and direct point-of-sale
age-verification/ID hardware actuation (see README.md).

- All code must be `.cljc` (portable Clojure, no JVM-only constructs).
- Tests must pass: `clojure -M:test`
- Commit messages should link to relevant ADRs or issues.

**This actor does NOT:**
- Finalize a food-safety clearance or otherwise stand in for a
  health/food-safety authority.
- Finalize an age-verification override for tobacco sales.
- Directly actuate or control point-of-sale age-verification/ID
  hardware.
- Perform market/health/tobacco-licensing-authority enforcement
  (stall/market permit issuance/suspension, health-department
  enforcement, compliance enforcement).

Contributions that cross these boundaries will be rejected.
