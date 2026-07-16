(ns marketstallops.store
  "SSoT for the ISIC-4781 stall/market food, beverage and tobacco retail
  COORDINATION actor, behind a `Store` protocol so the backend is a swap,
  not a rewrite -- the same seam every `cloud-itonami-isic-*` actor in
  this fleet uses.

  This actor coordinates the back-office operations of mobile/temporary
  market-stall and street-market vendors selling food, beverages, AND
  tobacco products (ISIC Rev.4 4781, 'Retail sale via stalls and markets
  of food, beverages and tobacco products'): inventory/sale/return
  sales-record logging, stall placement/staffing scheduling, inventory
  procurement (supply-order) coordination, and compliance-concern
  flagging (suspected food-safety-clearance failure, age-verification
  failure for tobacco sales, or stall/market-permit lapse). It NEVER
  finalizes a food-safety clearance, NEVER finalizes an age-verification
  override, and NEVER directly actuates point-of-sale age-verification/ID
  hardware -- see `marketstallops.governor`'s `scope-exclusion-violations`,
  a HARD, permanent, un-overridable block.

  `MemStore` -- atom of EDN. The deterministic default for dev/tests/demo
  (no deps). A `stalls` directory keyed by `:stall-id` STRING (never a
  keyword -- consistent keying from the start, avoiding the silent-miss
  bug that plagued an earlier shepherd attempt).

  A registered/verified stall record (vendor/business registration AND an
  independently verified stall/market food-safety-and-tobacco-retail
  permit) must exist before ANY proposal for that stall may ever commit
  or escalate -- `marketstallops.governor`'s `stall-unverified-violations`
  re-derives this from the stall's own `:registered?`/`:verified?` fields,
  never from proposal self-report, the SAME 'ground truth, not
  self-report' discipline every sibling actor's own governor uses.

  The ledger stays append-only: which stall a proposal targeted, which
  operation, on what basis, committed/held/escalated and approved by whom
  is always a query over an immutable log.")

(defprotocol Store
  (stall-record [s stall-id] "Registered stall record, or nil.
    Stall map: {:stall-id .. :name .. :kind .. :registered? bool :verified? bool}.
    :registered? -- vendor/business registration is on file.
    :verified?   -- stall/market food-safety-and-tobacco-retail permit
                    independently verified (never trust the proposal's
                    own claim).")
  (all-stalls [s])
  (ledger [s] "the append-only immutable decision-fact log")
  (coordination-log [s] "the append-only committed coordination-proposal history")
  (commit-record! [s record] "apply a committed proposal's record to the SSoT")
  (append-ledger! [s fact] "append one immutable decision fact")
  (with-stalls [s stalls] "replace/seed the stall directory (map stall-id->stall)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained stall directory covering both the happy path
  and the governor's own hard checks, so the actor + tests run offline."
  []
  {:stalls
   {"stall-1" {:stall-id "stall-1" :name "Riverside Market Food & Drink Stall" :kind :market-stall
               :registered? true :verified? true}
    "stall-2" {:stall-id "stall-2" :name "Night Bazaar Tobacco & Beverage Cart" :kind :mobile-cart
               :registered? true :verified? true}
    "stall-3" {:stall-id "stall-3" :name "Weekend Farmers Market Stall (permit pending)" :kind :market-stall
               :registered? true :verified? false}}})

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (stall-record [_ stall-id] (get-in @a [:stalls stall-id]))
  (all-stalls [_] (sort-by :stall-id (vals (:stalls @a))))
  (ledger [_] (:ledger @a))
  (coordination-log [_] (:coordination-log @a))
  (commit-record! [_ record]
    (swap! a update :coordination-log conj record)
    record)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-stalls [s stalls] (when (seq stalls) (swap! a assoc :stalls stalls)) s))

(defn seed-db
  "A MemStore seeded with the demo stall directory. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data) :ledger [] :coordination-log []))))

(defn mem-store
  "A MemStore seeded with an explicit `stalls` map (stall-id string ->
  stall map) -- the primary test/dev entry point. `stalls` may be empty
  (an unregistered-everywhere stall)."
  [stalls]
  (->MemStore (atom {:stalls (or stalls {}) :ledger [] :coordination-log []})))
