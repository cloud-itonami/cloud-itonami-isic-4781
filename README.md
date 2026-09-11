# cloud-itonami-isic-4781

**Retail sale via stalls and markets of food, beverages and tobacco products** — ISIC Rev.4 class 4781.

A coordination-only actor for mobile/temporary market-stall and street-market vendors selling food, beverages, AND tobacco products — behind an independent Governor that earns advisor trust through structured oversight: proposal → advise → govern → decide → commit|hold|escalate.

## Features

- **Closed proposal-op allowlist**: `log-sales-record`, `schedule-stall-operation`, `coordinate-supply-order`, `flag-compliance-concern` (all `:effect :propose`).
- **Three HARD governor checks** (permanent, un-overridable):
  1. **Stall unverified** — the target stall's vendor/business registration AND stall/market food-safety-and-tobacco-retail permit must exist AND be independently registered/verified in the store.
  2. **Effect is :propose** — any other `:effect` value is rejected.
  3. **Scope exclusion** — finalizing a food-safety clearance, finalizing an age-verification override (tobacco sales), direct point-of-sale age-verification/ID hardware actuation, and market/health/tobacco-licensing-authority enforcement (stall/market permit issuance/suspension, health-department enforcement, compliance enforcement) are permanently blocked. A structured-field companion check also inspects the proposal's `:value` for explicit finalization-intent booleans, belt-and-suspenders alongside the free-text scan.
- **Two ESCALATE (SOFT) gates**, either forces human sign-off:
  - `:flag-compliance-concern` — ALWAYS escalates, regardless of confidence or phase. A "flag a concern" op is never auto-commit-eligible and never finalizes a food-safety-clearance or age-verification decision itself — it only surfaces the concern for a human.
  - `:coordinate-supply-order` above a cost threshold — a large-value procurement proposal always needs a human sign-off.
  - (LLM confidence below the floor also escalates, as with every sibling actor.)
- **Staged rollout** (Phase 0→3):
  - Phase 0: read-only
  - Phase 1: sales-record logging only (approval-gated)
  - Phase 2: + stall-operation scheduling, supply-order proposals (approval-gated)
  - Phase 3: auto-commits clean, high-confidence, low-cost proposals (compliance concerns and high-cost supply orders always escalate)
- **Append-only audit ledger** — every decision is an immutable log entry.
- **langgraph-clj StateGraph** — one request = one supervised run; human-in-the-loop via `interrupt-before`.

## Out of scope (structural, not a rollout milestone)

This actor is **operations coordination only**. It never performs or authorizes:

- Finalizing a food-safety clearance.
- Finalizing an age-verification override (tobacco sales).
- Direct point-of-sale age-verification/ID hardware actuation or control.
- Market/health/tobacco-licensing-authority enforcement (stall/market permit issuance/suspension, health-department enforcement, compliance enforcement).

The governor's `scope-exclusion-violations` check re-scans every proposal for this failure mode independently of the advisor's own framing, and treats it as a HARD, permanent block regardless of confidence or how clean everything else is. A "flag a concern" op (`:flag-compliance-concern`) always escalates to a human and is never in any phase's `:auto` set — it only ever surfaces a concern, it never finalizes a food-safety-clearance or age-verification decision.

## Development

```bash
# Install dependencies (if inside the superproject, use :dev alias for local overrides)
kbb -M:dev -P

# Run tests
kbb -M:dev:test

# Run linter
kbb -M:lint

# Run demo
kbb -M:run
```

## Test suite

- `test/marketstallops/governor_test.cljk` — unit tests of governor hard checks, scope exclusion, structured-field violations, and a dedicated regression test asserting the default mock-advisor proposals never self-trip scope-exclusion
- `test/marketstallops/advisor_test.cljk` — advisor proposal shape and consistency
- `test/marketstallops/phase_test.cljk` — rollout phase logic
- `test/marketstallops/governor_contract_test.cljk` — full graph integration, audit trail
- `test/marketstallops/store_contract_test.cljk` — Store protocol and MemStore implementation

## Modules

- `marketstallops.store` — SSoT (MemStore, String-keyed stall directory, append-only ledger)
- `marketstallops.advisor` — contained intelligence node (mock + real-LLM seam)
- `marketstallops.governor` — independent compliance layer
- `marketstallops.phase` — staged rollout (0→3)
- `marketstallops.operation` — langgraph-clj StateGraph
- `marketstallops.sim` — demo driver

## License

AGPL-3.0-or-later. See LICENSE file.

## Governance

This actor is part of the cloud-itonami Wave 2 (coordination/logistics/trade) fleet. See ADR-2607121000 and the `cloud-itonami-isic-4781-stall-market-food-retail-coverage` ADR in `com-junkawasaki/root` for design decisions.
