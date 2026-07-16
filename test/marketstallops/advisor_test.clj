(ns marketstallops.advisor-test
  "Unit tests of `marketstallops.advisor` proposal generation."
  (:require [clojure.test :refer [deftest is testing]]
            [marketstallops.advisor :as adv]
            [marketstallops.store :as store]))

(def db (store/seed-db))

(deftest propose-sales-record-shape
  (testing "sales-record proposal has correct shape and fields"
    (let [p (adv/infer db {:op :log-sales-record
                           :stall-id "stall-1"
                           :patch {:units-sold 24 :item "grilled skewers"}})]
      (is (= :log-sales-record (:op p)))
      (is (= "stall-1" (:stall-id p)))
      (is (= :propose (:effect p)))
      (is (<= 0 (:confidence p) 1))
      (is (map? (:value p)))
      (is (contains? (:value p) :stall-id)))))

(deftest propose-stall-operation-shape
  (testing "stall-operation proposal has correct shape"
    (let [p (adv/infer db {:op :schedule-stall-operation
                           :stall-id "stall-2"
                           :patch {:pitch "night-bazaar-row-2" :date "2026-07-20"}})]
      (is (= :schedule-stall-operation (:op p)))
      (is (= "stall-2" (:stall-id p)))
      (is (= :propose (:effect p))))))

(deftest propose-supply-order-shape
  (testing "supply-order proposal has correct shape"
    (let [p (adv/infer db {:op :coordinate-supply-order
                           :stall-id "stall-1"
                           :patch {:item "napkins and packaging" :quantity 500 :estimated-cost 85.0}})]
      (is (= :coordinate-supply-order (:op p)))
      (is (= :propose (:effect p)))
      (is (string? (:summary p))))))

(deftest propose-compliance-concern-shape
  (testing "compliance-concern proposal always escalates"
    (let [p (adv/infer db {:op :flag-compliance-concern
                           :stall-id "stall-1"
                           :patch {:concern "suspected underage tobacco purchase attempt"}})]
      (is (= :flag-compliance-concern (:op p)))
      (is (= :propose (:effect p)))
      (is (string? (:summary p))))))

(deftest all-proposals-effect-is-always-propose
  (testing "every proposal type has :effect :propose, never direct actuation"
    (doseq [op [:log-sales-record :schedule-stall-operation :coordinate-supply-order
                :flag-compliance-concern]]
      (let [p (adv/infer db {:op op :stall-id "stall-1" :patch {}})]
        (is (= :propose (:effect p))
            (str "op " op " must have :effect :propose"))))))

(deftest rationale-string-is-present
  (testing "every proposal has a rationale explaining the advisor's thinking"
    (doseq [op [:log-sales-record :schedule-stall-operation :coordinate-supply-order
                :flag-compliance-concern]]
      (let [p (adv/infer db {:op op :stall-id "stall-1" :patch {}})]
        (is (string? (:rationale p))
            (str "op " op " must have a :rationale string"))))))
