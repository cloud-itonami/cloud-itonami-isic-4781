(ns marketstallops.advisor
  "MarketStallRetailAdvisor -- the *contained intelligence node* for the
  ISIC-4781 stall/market food, beverage and tobacco retail
  operations-coordination actor (mobile market stalls, street-market
  carts, temporary bazaar vendors selling food, beverages, and tobacco
  products).

  It drafts exactly four kinds of back-office proposal from a closed
  allowlist: sales-record logging (inventory/sale/return), stall
  placement/staffing scheduling, supply-order coordination (inventory
  procurement), and compliance-concern flagging (suspected
  food-safety-clearance failure, age-verification failure for tobacco
  sales, or stall/market-permit lapse). CRITICAL: it is a
  smart-but-untrusted advisor. It returns a *proposal* (with a rationale
  + the fields it cited), never a committed record and NEVER a direct
  actuation -- every proposal's `:effect` is always `:propose`. Every
  output is censored downstream by `marketstallops.governor` before
  anything touches the SSoT.

  This advisor NEVER drafts a finalized food-safety-clearance decision, a
  finalized age-verification-override decision, direct point-of-sale
  age-verification/ID hardware actuation, or any other market/health/
  tobacco-licensing-authority action (permit issuance/suspension,
  compliance enforcement) -- those are permanently out of scope for this
  actor, not merely un-implemented. `marketstallops.governor`'s
  `scope-exclusion-violations` independently re-scans every proposal for
  exactly this failure mode (a compromised or confused advisor drifting
  into scope it must never touch) and HARD-holds it, regardless of
  confidence or op.

  Like every sibling actor's advisor, this is a deterministic mock so the
  actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:op         kw             ; echoes the request op
     :stall-id   str
     :summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the scope-exclusion gate
     :cites      [str ..]       ; facts/sources the advisor used -- SCANNED too
     :effect     :propose       ; ALWAYS :propose -- never a direct actuation
     :value      map            ; the draft payload a human/system would review
     :confidence 0..1}")

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

;; ----------------------------- proposal generators -----------------------------

(defn- propose-sales-record
  "Draft an inventory/sale/return log entry. Pure logging of observed
  operations (units sold, stock on hand, returns processed) -- never a
  food-safety or age-verification judgement."
  [_db {:keys [stall-id patch]}]
  {:op         :log-sales-record
   :stall-id   stall-id
   :summary    (str stall-id " の売上/在庫/返品記録を記録: " (pr-str (keys patch)))
   :rationale  "販売数量・在庫・返品の観察記録のみ。食品安全許可・年齢確認の判断は行わない。"
   :cites      [stall-id]
   :effect     :propose
   :value      (merge {:stall-id stall-id} patch)
   :confidence 0.93})

(defn- propose-stall-operation
  "Draft a stall placement/staffing scheduling proposal (a calendar/roster
  entry, never a direct point-of-sale age-verification/ID hardware
  actuation, and never a market-space allocation ruling by the market
  operator)."
  [_db {:keys [stall-id patch]}]
  {:op         :schedule-stall-operation
   :stall-id   stall-id
   :summary    (str stall-id " の出店配置・スタッフ配置予定を提案: " (pr-str (keys patch)))
   :rationale  "屋台/市場の出店配置と人員配置の調整提案のみ。許可証の確認や年齢確認端末の直接操作は行わない。"
   :cites      [stall-id]
   :effect     :propose
   :value      (merge {:stall-id stall-id} patch)
   :confidence 0.88})

(defn- propose-supply-order
  "Draft an inventory procurement coordination request (food ingredients,
  beverages, tobacco product stock, packaging -- never a finalized
  purchase order; a human always confirms procurement)."
  [_db {:keys [stall-id patch]}]
  {:op         :coordinate-supply-order
   :stall-id   stall-id
   :summary    (str stall-id " に関連する仕入れ調達オーダーを提案: " (pr-str (keys patch)))
   :rationale  "食品・飲料・タバコ製品などの仕入れ調整提案のみ。確定発注は人間が行う。"
   :cites      [stall-id]
   :effect     :propose
   :value      (merge {:stall-id stall-id} patch)
   :confidence 0.90})

(defn- propose-compliance-concern
  "Surface a compliance concern (suspected food-safety-clearance failure,
  age-verification failure for a tobacco sale, or a stall/market-permit
  lapse) for HUMAN triage. This op ALWAYS escalates in
  `marketstallops.governor` -- never auto-committed at any phase --
  regardless of how confident the advisor is that the concern is real.
  This op only ever SURFACES a concern; it never itself finalizes a
  food-safety clearance or an age-verification override."
  [_db {:keys [stall-id patch]}]
  {:op         :flag-compliance-concern
   :stall-id   stall-id
   :summary    (str stall-id " のコンプライアンス懸念フラグ: " (pr-str (:concern patch "unknown")))
   :rationale  "食品安全または年齢確認(タバコ販売)に関する懸念事実の報告。常に人間の確認とその後の対応が必要。"
   :cites      [stall-id]
   :effect     :propose
   :value      (merge {:stall-id stall-id} patch)
   :confidence (or (:confidence patch) 0.85)})

;; ----------------------------- default mock advisor -----------------------------

(defn infer
  "Mock advisor: routes to the correct proposal generator."
  [_db {:keys [op out-of-scope?] :as request}]
  (let [proposal (case op
                   :log-sales-record (propose-sales-record _db request)
                   :schedule-stall-operation (propose-stall-operation _db request)
                   :coordinate-supply-order (propose-supply-order _db request)
                   :flag-compliance-concern (propose-compliance-concern _db request)
                   {})]
    ;; Test hook: allow injecting scope-excluded content to exercise the
    ;; governor's scope-exclusion block end-to-end. Must be cleared before
    ;; production use.
    (if out-of-scope?
      (update proposal :rationale str " -- actually finalized the food safety clearance and overrode the age verification for this tobacco sale")
      proposal)))

(defn trace
  "Audit fact for a proposal generated by this advisor."
  [_request proposal]
  {:t       :advisor-proposal
   :op      (:op proposal)
   :stall-id (:stall-id proposal)
   :summary (:summary proposal)
   :confidence (:confidence proposal)})

(defn mock-advisor
  "The deterministic default advisor for offline demo/test."
  []
  (reify Advisor
    (-advise [_ _store request]
      (infer nil request))))
