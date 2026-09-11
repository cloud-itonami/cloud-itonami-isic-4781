(ns marketstallops.governor
  "MarketStallRetailGovernor -- the independent compliance layer that
  earns the MarketStallRetailAdvisor the right to commit. The advisor has
  no notion of whether a stall is actually registered and verified (i.e.
  its vendor/business registration AND stall/market food-safety-and-
  tobacco-retail permit are on file), whether its own proposed `:effect`
  secretly claims a direct actuation instead of a mere proposal, or
  whether it has silently drifted into a permanently out-of-scope
  decision area, so this MUST be a separate system able to *reject* a
  proposal and fall back to HOLD.

  This actor's scope is deliberately narrow -- OPERATIONS COORDINATION
  ONLY (sales-record logging, stall-operation scheduling, supply-order
  coordination, compliance-concern flagging) for mobile market-stall and
  street-market vendors selling food, beverages, and tobacco products.
  It NEVER performs or authorizes:
    - finalizing a food-safety clearance
    - finalizing an age-verification override (tobacco sales)
    - direct point-of-sale age-verification/ID hardware actuation or
      control
    - market/health/tobacco-licensing-authority enforcement (stall/market
      permit issuance/suspension, health-department enforcement,
      compliance enforcement)

  Three HARD checks, ALL permanent, un-overridable by any human approval:

    1. Stall unverified          -- the target stall (vendor/business
                                    registration + stall/market
                                    food-safety-and-tobacco-retail
                                    permit) record must exist AND be
                                    independently confirmed
                                    `:registered?`/`:verified?` in the
                                    store before ANY proposal for it may
                                    commit or even escalate. Never trusts
                                    a proposal's own claim about the
                                    stall -- re-derived from the stall's
                                    own record, the same 'ground truth,
                                    not self-report' discipline every
                                    sibling actor's governor uses.
    2. Effect not :propose       -- every proposal's `:effect` MUST be
                                    `:propose`. Any other effect value
                                    is, by construction, a claim to
                                    directly actuate/commit outside
                                    governance -- HARD block, not merely
                                    low-confidence.
    3. Scope exclusion           -- ANY proposal (regardless of op)
                                    whose op, rationale, summary,
                                    citations, or draft value touches
                                    food-safety-clearance-finalization/
                                    age-verification-override-
                                    finalization/point-of-sale-age-
                                    verification-hardware-actuation/
                                    market-health-tobacco-licensing-
                                    authority territory is a HARD,
                                    PERMANENT block -- this actor's
                                    charter excludes that territory
                                    structurally, not as a rollout
                                    milestone. Evaluated UNCONDITIONALLY
                                    on every proposal. An op outside the
                                    closed four-op allowlist is the SAME
                                    failure mode (an advisor proposing
                                    something it was never authorized to
                                    propose) and is folded into this same
                                    check. A STRUCTURED-FIELD companion
                                    check (`structured-scope-violations`)
                                    also inspects `:value` for explicit
                                    finalization-intent booleans, per
                                    this fleet's most recent best
                                    practice of preferring structured
                                    fields over free-text scanning where
                                    possible -- belt-and-suspenders with
                                    the text scan, not a replacement.

  Two ESCALATE (SOFT) gates, either forces human sign-off:
    - LLM confidence below the floor.
    - The op is `:flag-compliance-concern` -- ALWAYS escalates to a
      human, regardless of confidence, regardless of how clean the
      proposal otherwise is. This op only ever SURFACES a concern for a
      human -- it never itself finalizes any food-safety clearance or
      age-verification override. `marketstallops.phase` independently
      agrees: `:flag-compliance-concern` is never a member of any
      phase's `:auto` set either -- two layers, not one.
    - A `:coordinate-supply-order` whose drafted `:value` names an
      `:estimated-cost` above `supply-cost-threshold` -- a large-value
      procurement proposal always needs a human sign-off, even when the
      governor and phase would otherwise allow auto-commit.

  Scope-exclusion-term discipline (see ADR-2607121000 / sibling actors'
  own fix history): terms are phrased as the finalization/execution
  ACTION (e.g. \"finalize the food safety clearance\", \"control the id
  scanner\"), never as a bare noun (e.g. bare \"food\" or bare
  \"verification\") -- a bare noun would accidentally match this SAME
  namespace's own default mock-advisor disclaimer text for a legitimate,
  allowed proposal (e.g. `advisor`'s own \"...食品安全許可・年齢確認の判断は行わない\" /
  \"...年齢確認端末の直接操作は行わない\" rationale strings), causing the
  actor to self-block on its own happy path. The point-of-sale
  age-verification/ID-hardware-actuation terms below are deliberately kept
  ENGLISH-ONLY (no Japanese equivalent) because this namespace's own
  Japanese default proposal rationale text legitimately discusses NOT
  touching such hardware -- see
  `marketstallops-scope-exclusion-test`'s dedicated
  `default-mock-advisor-proposals-never-self-trip-scope-exclusion` test,
  which asserts every default op's proposal clears this check."
  (:require [kotoba.lang.text :as str]
            [marketstallops.store :as store]))

(def confidence-floor 0.6)

(def supply-cost-threshold
  "Example stall/market food-beverage-tobacco retail procurement
  threshold (USD-equivalent units, domain-illustrative -- not a universal
  cross-domain constant). A `:coordinate-supply-order` proposal citing an
  `:estimated-cost` above this value ALWAYS escalates to human sign-off,
  regardless of confidence or rollout phase."
  500.0)

(def allowed-ops
  "The closed proposal-op allowlist -- an op outside this set is a scope
  violation by construction (see `scope-exclusion-violations`). No op in
  this set ever finalizes a food-safety clearance or an age-verification
  override -- that authority is permanently out of scope, see
  `scope-excluded-terms`."
  #{:log-sales-record :schedule-stall-operation
    :coordinate-supply-order :flag-compliance-concern})

(def always-escalate-ops
  "Ops that ALWAYS require human sign-off, clean or not.
  `:flag-compliance-concern` only ever SURFACES a concern -- it must
  never be auto-commit-eligible, in any phase, for any confidence."
  #{:flag-compliance-concern})

(def scope-excluded-terms
  "Case-insensitive substrings that mark a proposal as touching a
  permanently out-of-scope decision area -- finalizing a food-safety
  clearance, finalizing an age-verification override (tobacco sales),
  directly actuating point-of-sale age-verification/ID hardware, or
  market/health/tobacco-licensing-authority enforcement. Scanned across
  the proposal's op/summary/rationale/cites/value, never trusting the
  advisor's own framing of its intent.

  Every term is phrased as the finalization/execution ACTION, not a bare
  noun -- see the namespace docstring's self-trip-avoidance note."
  ["finalize the food safety clearance" "finalize food safety clearance"
   "finalized the food safety clearance" "finalizes the food safety clearance"
   "food safety clearance override" "override the food safety clearance"
   "overrides the food safety clearance" "overrode the food safety clearance"
   "bypass food safety clearance" "waive food safety clearance"
   "ignore food safety clearance" "skip food safety clearance" "disable food safety clearance"
   "食品安全許可の上書き" "食品安全許可を上書き" "食品安全許可の最終判断"
   "食品安全許可を確定" "食品安全許可の無効化" "食品安全許可を無効化"
   "finalize the age verification" "finalize age verification"
   "finalized the age verification" "finalizes the age verification"
   "age verification override" "override the age verification"
   "overrides the age verification" "overrode the age verification"
   "bypass age verification" "waive age verification"
   "ignore age verification" "skip age verification" "disable age verification"
   "年齢確認の上書き" "年齢確認を上書き" "年齢確認の最終判断"
   "年齢確認を確定" "年齢確認の無効化" "年齢確認を無効化"
   "control the id scanner" "actuate the id scanner" "override the id scanner"
   "shut down the id scanner" "power off the id scanner"
   "control the age verification terminal" "actuate the age verification terminal"
   "override the age verification terminal" "disable the age verification terminal"
   "market health authority enforcement" "health department enforcement"
   "food safety authority enforcement" "tobacco licensing authority enforcement"
   "tobacco retail license suspension"
   "license suspension" "license-suspension" "permit revocation" "permit-revocation"
   "regulatory clearance" "compliance sign-off"
   "食品衛生当局の摘発" "屋台営業許可取消" "市場出店許可取消" "タバコ販売許可取消"])

;; ----------------------------- checks -----------------------------

(defn- stall-unverified-violations
  "The target stall (vendor/business registration + stall/market
  food-safety-and-tobacco-retail permit) must exist AND be independently
  `:registered?`/`:verified?` in the store -- never trust the proposal's
  own `:stall-id` claim without a stall lookup."
  [{:keys [stall-id]} st]
  (let [s (store/stall-record st stall-id)]
    (when-not (and s (:registered? s) (:verified? s))
      [{:rule :stall-unverified
        :detail (str stall-id " は未登録または未検証の屋台/市場出店(営業登録/食品安全・タバコ販売許可)-- いかなる提案も進められない")}])))

(defn- effect-not-propose-violations
  "`:effect` must ALWAYS be `:propose` -- any other value is a claim to
  directly actuate/commit outside governance."
  [proposal]
  (when (not= :propose (:effect proposal))
    [{:rule :effect-not-propose
      :detail (str ":effect は :propose のみ許可されるが " (pr-str (:effect proposal)) " が提案された")}]))

(defn- text-blob
  "Flatten every advisor-authored field on a proposal into one
  lower-cased blob the scope-exclusion scan checks."
  [proposal]
  (str/lower (pr-str (select-keys proposal [:op :summary :rationale :cites :value]))))

(defn- structured-scope-violations
  "Structured-field companion to the free-text scan below: if the
  advisor's drafted `:value` carries an EXPLICIT finalization-intent
  boolean (e.g. a compromised/confused advisor setting
  `:finalizes-food-safety-clearance?` or
  `:finalizes-age-verification-override?` to `true`), that is a HARD,
  PERMANENT block regardless of any other field, and regardless of
  whether the free-text scan below also happens to catch it. Preferring
  structured fields over free-text scanning where possible is this
  fleet's most recent best practice for avoiding the self-trip bug class
  structurally -- this check is belt-and-suspenders alongside the
  text-blob scan, not a replacement for it."
  [proposal]
  (let [v (:value proposal)]
    (when (or (true? (:finalizes-food-safety-clearance? v))
              (true? (:finalizes-age-verification-override? v))
              (true? (:overrides-food-safety-clearance? v))
              (true? (:overrides-age-verification-decision? v)))
      [{:rule :scope-excluded
        :detail "提案の value に食品安全許可上書き/年齢確認の最終判断を示す構造化フラグが立っている -- 永久に禁止"}])))

(defn- scope-exclusion-violations
  "HARD, PERMANENT block: a proposal outside the closed op allowlist, one
  whose content touches food-safety-clearance-finalization/age-
  verification-override-finalization/point-of-sale-age-verification-
  hardware-actuation/market-health-tobacco-licensing-authority territory,
  or one carrying an explicit structured finalization-intent flag,
  regardless of confidence or how clean every other check is. Evaluated
  UNCONDITIONALLY on every proposal."
  [proposal]
  (let [op (:op proposal)
        blob (text-blob proposal)]
    (cond
      (not (contains? allowed-ops op))
      [{:rule :op-not-allowed
        :detail (str (pr-str op) " は許可された操作(closed allowlist)に含まれない")}]

      (some #(str/includes? blob %) scope-excluded-terms)
      [{:rule :scope-excluded
        :detail "食品安全許可の最終判断/年齢確認の上書き/POS年齢確認機器の直接操作/市場・保健・タバコ販売許可当局の判断領域に触れる提案は永久に禁止"}]

      :else
      (structured-scope-violations proposal))))

(defn- high-cost-supply-order?
  "A `:coordinate-supply-order` proposal citing an `:estimated-cost`
  above `supply-cost-threshold` -- always needs human sign-off (SOFT
  escalate, not a hard block: the order itself is in scope, only its
  size requires a human)."
  [proposal]
  (and (= :coordinate-supply-order (:op proposal))
       (some-> proposal :value :estimated-cost (> supply-cost-threshold))))

(defn check
  "Censors a MarketStallRetailAdvisor proposal against the governor
  rules. Returns {:ok? bool :violations [..] :confidence c :escalate?
  bool :high-stakes? bool :hard? bool}."
  [request _context proposal store]
  (let [stall-id (or (:stall-id proposal) (:stall-id request))
        hard (into []
                   (concat (stall-unverified-violations {:stall-id stall-id} store)
                           (effect-not-propose-violations proposal)
                           (scope-exclusion-violations proposal)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (or (always-escalate-ops (:op proposal))
                              (high-cost-supply-order? proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :stall-id   (:stall-id request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
