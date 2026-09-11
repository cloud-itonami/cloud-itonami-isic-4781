(ns marketstallops.governor-test
  "Pure unit tests of `marketstallops.governor/check` against hand-built
  proposals -- the fast, focused complement to `governor-contract-test`'s
  full-graph integration coverage."
  (:require [clojure.test :refer [deftest is testing]]
            [marketstallops.advisor :as adv]
            [marketstallops.governor :as gov]
            [marketstallops.store :as store]))

(def stall-1 {:stall-id "stall-1" :name "Riverside Market Food & Drink Stall" :registered? true :verified? true})
(def stall-3 {:stall-id "stall-3" :name "Weekend Farmers Market Stall" :registered? true :verified? false})

(defn- clean-proposal [op stall-id]
  {:op op :stall-id stall-id :summary "s" :rationale "routine retail coordination"
   :cites [stall-id] :effect :propose :value {} :confidence 0.85})

(deftest stall-unregistered-is-hard
  (testing "no stall record at all -> HARD hold"
    (let [s (store/mem-store {"stall-1" stall-1})
          verdict (gov/check {} nil (clean-proposal :log-sales-record "unknown-stall") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:stall-unverified} (map :rule (:violations verdict)))))))

(deftest stall-unverified-is-hard
  (testing "stall registered but not yet verified -> HARD hold"
    (let [s (store/mem-store {"stall-3" stall-3})
          verdict (gov/check {} nil (clean-proposal :log-sales-record "stall-3") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:stall-unverified} (map :rule (:violations verdict)))))))

(deftest effect-not-propose-is-hard
  (testing "any :effect other than :propose is a HARD, un-overridable block"
    (let [s (store/mem-store {"stall-1" stall-1})
          verdict (gov/check {} nil (assoc (clean-proposal :schedule-stall-operation "stall-1") :effect :commit) s)]
      (is (true? (:hard? verdict)))
      (is (some #{:effect-not-propose} (map :rule (:violations verdict)))))))

(deftest op-outside-allowlist-is-hard
  (testing "an op outside the closed four-op allowlist is a scope violation"
    (let [s (store/mem-store {"stall-1" stall-1})
          verdict (gov/check {} nil (clean-proposal :finalize-food-safety-clearance "stall-1") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:op-not-allowed} (map :rule (:violations verdict)))))))

(deftest food-safety-clearance-content-is-hard-and-permanent
  (testing "a proposal whose rationale touches finalizing a food-safety clearance is HARD-blocked regardless of op/confidence"
    (let [s (store/mem-store {"stall-1" stall-1})
          poisoned (assoc (clean-proposal :log-sales-record "stall-1")
                          :rationale "finalized the food safety clearance for this stall"
                          :confidence 0.99)
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest age-verification-override-content-is-hard
  (testing "a proposal touching an age-verification override is HARD-blocked, same as food-safety"
    (let [s (store/mem-store {"stall-1" stall-1})
          poisoned (assoc (clean-proposal :log-sales-record "stall-1")
                          :rationale "decided to override the age verification for this tobacco sale"
                          :confidence 0.90)
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest id-scanner-direct-control-content-is-hard
  (testing "a proposal touching direct point-of-sale ID-scanner/age-verification-terminal actuation is HARD-blocked"
    (let [s (store/mem-store {"stall-1" stall-1})
          poisoned (assoc (clean-proposal :schedule-stall-operation "stall-1")
                          :summary "actuate the id scanner: control the id scanner remotely")
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest licensing-authority-content-is-hard
  (testing "a proposal touching market/health/tobacco-licensing-authority enforcement is HARD-blocked"
    (let [s (store/mem-store {"stall-1" stall-1})
          poisoned (assoc (clean-proposal :coordinate-supply-order "stall-1")
                          :summary "contact tobacco licensing authority enforcement for regulatory clearance and license suspension review")
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest structured-food-safety-flag-is-hard
  (testing "an explicit structured :finalizes-food-safety-clearance? true flag on :value is HARD-blocked even with clean text fields"
    (let [s (store/mem-store {"stall-1" stall-1})
          poisoned (assoc (clean-proposal :log-sales-record "stall-1")
                          :value {:finalizes-food-safety-clearance? true})
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest structured-age-verification-flag-is-hard
  (testing "an explicit structured :finalizes-age-verification-override? true flag on :value is HARD-blocked even with clean text fields"
    (let [s (store/mem-store {"stall-1" stall-1})
          poisoned (assoc (clean-proposal :flag-compliance-concern "stall-1")
                          :value {:finalizes-age-verification-override? true})
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest legitimate-compliance-concern-is-not-scope-excluded
  (testing "flagging observed food-safety/age-verification-failure concerns as a COMPLIANCE CONCERN (not a finalized decision) never trips scope-exclusion -- this actor's core valid use case must not be self-blocked"
    (let [s (store/mem-store {"stall-1" stall-1})
          concern (assoc (clean-proposal :flag-compliance-concern "stall-1")
                         :value {:concern "customer buying tobacco declined to present ID, suspected underage purchase attempt"})
          verdict (gov/check {} nil concern s)]
      (is (empty? (filter #(= :scope-excluded (:rule %)) (:violations verdict)))
          "raw observation content (food-safety/age-verification failure) is exactly what this op exists to surface"))))

(deftest compliance-concern-always-escalates-clean
  (testing ":flag-compliance-concern is always high-stakes/escalate, even when otherwise clean and high confidence"
    (let [s (store/mem-store {"stall-1" stall-1})
          verdict (gov/check {} nil (assoc (clean-proposal :flag-compliance-concern "stall-1") :confidence 0.99) s)]
      (is (false? (:hard? verdict)))
      (is (true? (:high-stakes? verdict)))
      (is (true? (:escalate? verdict))))))

(deftest high-cost-supply-order-always-escalates
  (testing "a :coordinate-supply-order above the cost threshold is high-stakes/escalate, even when otherwise clean and high confidence"
    (let [s (store/mem-store {"stall-1" stall-1})
          expensive (assoc (clean-proposal :coordinate-supply-order "stall-1")
                           :value {:item "bulk tobacco product restock" :estimated-cost 5000.0}
                           :confidence 0.97)
          verdict (gov/check {} nil expensive s)]
      (is (false? (:hard? verdict)))
      (is (true? (:high-stakes? verdict)))
      (is (true? (:escalate? verdict))))))

(deftest low-cost-supply-order-does-not-force-escalate
  (testing "a :coordinate-supply-order at or below the cost threshold does not trip the high-cost escalate gate"
    (let [s (store/mem-store {"stall-1" stall-1})
          cheap (assoc (clean-proposal :coordinate-supply-order "stall-1")
                       :value {:item "napkins and packaging" :estimated-cost 85.0}
                       :confidence 0.9)
          verdict (gov/check {} nil cheap s)]
      (is (false? (:hard? verdict)))
      (is (false? (:high-stakes? verdict)))
      (is (false? (:escalate? verdict))))))

;; ----------------------------------------------------------------------
;; CRITICAL guardrail regression test: multiple sibling actors in this
;; fleet have independently discovered and fixed the SAME bug class --
;; a governor scope-exclusion term phrased as a bare noun (e.g. bare
;; "food" or "verification") accidentally matches inside the mock
;; advisor's OWN default rationale/disclaimer text for a legitimate,
;; allowed proposal, causing the actor to self-block on its own happy
;; path. This test asserts every default mock-advisor proposal, for
;; every op in the closed allowlist, at a REGISTERED+VERIFIED stall,
;; clears the governor with `:scope-excluded` absent from its
;; violations (regardless of `:hard?`/`:escalate?` -- some ops legally
;; escalate, e.g. :flag-compliance-concern, but MUST NOT self-trip the
;; scope-exclusion check to get there).
;; ----------------------------------------------------------------------
(deftest default-mock-advisor-proposals-never-self-trip-scope-exclusion
  (testing "the default mock advisor's own proposals, for every allowed op, never trigger :scope-excluded"
    (let [s (store/mem-store {"stall-1" stall-1})]
      (doseq [op [:log-sales-record :schedule-stall-operation :coordinate-supply-order
                  :flag-compliance-concern]]
        (let [proposal (adv/infer nil {:op op :stall-id "stall-1"
                                        :patch {:units-sold 10 :item "test"
                                                :estimated-cost 85.0
                                                :concern "routine ID check"}})
              verdict (gov/check {:stall-id "stall-1"} nil proposal s)]
          (is (empty? (filter #(= :scope-excluded (:rule %)) (:violations verdict)))
              (str "default mock advisor's own proposal for " op
                   " must never self-trip :scope-excluded -- rationale/summary: "
                   (pr-str (select-keys proposal [:rationale :summary])))))))))
