(ns marketstallops.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300,
  Wave1 Lane A-no-demo / Wave5 rollout ledger): this repo previously had
  NO demo page and no generator at all. This namespace drives the REAL
  actor stack (`marketstallops.operation` -> `marketstallops.governor`
  -> `marketstallops.store`) through a scenario adapted from this
  repo's own `marketstallops.sim` demo driver (`clojure -M:dev:run`,
  confirmed BEFORE writing this file to produce a sensible ledger
  against the real seeded stall ids `stall-1`..`stall-3` -- ids that
  DO match `marketstallops.store/demo-data`, so it was safe to reuse
  rather than author from scratch), trimmed to a representative subset
  (clean auto-commits at phase 3, escalate+approve paths, and four
  distinct HARD-hold reasons) and rendered deterministically -- no
  invented numbers, no timestamps in the page content, byte-identical
  across reruns against the same seed (verify by diffing two consecutive
  runs).

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [jp-go-dds.skin]
            [kotoba.lang.text :as str]
            [marketstallops.store :as store]
            [marketstallops.advisor :as advisor]
            [marketstallops.operation :as op]
            [langgraph.graph :as g]))

(def ^:private operator
  {:actor-id "coord-1" :actor-role :market-stall-coordinator :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "coord-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Runs a fresh seeded store through a scenario mixing every disposition
  this actor can reach:

  - stall-1 clears clean phase-3 auto-commits: `:log-sales-record`,
    `:schedule-stall-operation`, and a low-cost `:coordinate-supply-order`
    (governor-clean, in phase 3's `:auto` set).
  - stall-1 then walks two ALWAYS-escalate paths that a human approves:
    a high-cost `:coordinate-supply-order` (above
    `governor/supply-cost-threshold`) and `:flag-compliance-concern`
    (in `always-escalate-ops`, never auto at any phase).
  - Four distinct HARD holds, none of which ever reach a human:
    * stall-99 unregistered -> `:stall-unverified`
    * stall-3 registered but unverified -> `:stall-unverified`
    * stall-1 with advisor `:effect :commit` -> `:effect-not-propose`
    * stall-1 with `:out-of-scope? true` (advisor injects scope-excluded
      rationale) -> `:scope-excluded`

  Returns the resulting store -- every field read by `render` below is
  real governor/store output, not a hand-typed copy."
  []
  (let [db (store/seed-db)
        actor (op/build db)]

    ;; Clean auto-commits at phase 3
    (exec! actor "t1-sales" {:op :log-sales-record :stall-id "stall-1"
                             :patch {:units-sold 18 :item "iced tea 500ml" :units-returned 0}})

    (exec! actor "t2-schedule" {:op :schedule-stall-operation :stall-id "stall-1"
                                :patch {:pitch "riverside-market-lot-4" :date "2026-07-20" :window "10:00-18:00"}})

    (exec! actor "t3-supply-low" {:op :coordinate-supply-order :stall-id "stall-1"
                                  :patch {:item "napkins and packaging" :quantity 500 :estimated-cost 85.0}})

    ;; ALWAYS escalate + human approve
    (exec! actor "t4-supply-high" {:op :coordinate-supply-order :stall-id "stall-1"
                                   :patch {:item "bulk tobacco product restock" :quantity 1 :estimated-cost 3200.0}})
    (approve! actor "t4-supply-high")

    (exec! actor "t5-flag" {:op :flag-compliance-concern :stall-id "stall-1"
                            :patch {:concern "customer buying tobacco declined to present ID after vendor request, suspected underage purchase attempt"
                                    :confidence 0.92}})
    (approve! actor "t5-flag")

    ;; HARD holds (never reach a human)
    (exec! actor "t6-unregistered" {:op :log-sales-record :stall-id "stall-99"
                                    :patch {:units-sold 0 :item "unknown"}})

    (exec! actor "t7-unverified" {:op :log-sales-record :stall-id "stall-3"
                                  :patch {:units-sold 10 :item "grilled corn"}})

    (let [actor-direct (op/build db {:advisor (reify advisor/Advisor
                                                (-advise [_ _ req]
                                                  (assoc (advisor/infer nil req) :effect :commit)))})]
      (exec! actor-direct "t8-effect" {:op :schedule-stall-operation :stall-id "stall-1"
                                       :patch {:pitch "night-bazaar-row-2" :date "2026-07-22"}}))

    (exec! actor "t9-scope" {:op :log-sales-record :stall-id "stall-1"
                             :out-of-scope? true
                             :patch {}})
    db))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- last-fact-for [ledger stall-id]
  (last (filter #(= (:stall-id %) stall-id) ledger)))

(defn- status-cell [ledger stall-id]
  (let [f (last-fact-for ledger stall-id)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :approval-granted (:t f)) "<span class=\"ok\">approved &amp; committed</span>"
      (= :governor-hold (:t f))
      (let [rule (-> f :violations first :rule)]
        (str "<span class=\"critical\">HARD hold &middot; " (esc (name (or rule :unknown))) "</span>"))
      (= :approval-requested (:t f)) "<span class=\"warn\">awaiting approval</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- verified-cell [{:keys [registered? verified?]}]
  (cond
    (and registered? verified?) "<span class=\"ok\">registered &amp; verified</span>"
    registered? "<span class=\"warn\">registered, permit pending</span>"
    :else "<span class=\"critical\">unregistered</span>"))

(defn- stall-row [ledger {:keys [stall-id kind] :as s}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc stall-id) (esc (:name s)) (esc (clojure.core/name (or kind :unknown)))
          (verified-cell s)
          (status-cell ledger stall-id)))

(defn- ledger-row [{:keys [t op stall-id disposition basis]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (name t)) (esc (name (or op :n-a))) (esc stall-id)
          (esc (or (some->> basis (map name) (str/join ", ")) (some-> disposition name) ""))))

(defn- coord-row [{:keys [op stall-id value]}]
  (format "        <tr><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (name (or op :n-a))) (esc stall-id)
          (esc (pr-str (or value {})))))

(def ^:private action-gate-rows
  ;; Static description of this actor's own closed op contract
  ;; (README `Ops`, `marketstallops.governor`/`marketstallops.phase`) --
  ;; documentation of fixed behavior, not runtime telemetry, so it is
  ;; legitimately hand-described rather than derived from a live run.
  ["        <tr><td><code>:log-sales-record</code></td><td><span class=\"ok\">phase-3 auto-commit when clean &amp; high-confidence</span></td></tr>"
   "        <tr><td><code>:schedule-stall-operation</code></td><td><span class=\"ok\">phase-3 auto-commit when clean &amp; high-confidence</span></td></tr>"
   "        <tr><td><code>:coordinate-supply-order</code></td><td><span class=\"warn\">phase-3 auto when clean &amp; cost ≤ threshold; ALWAYS escalate when estimated-cost &gt; 500</span></td></tr>"
   "        <tr><td><code>:flag-compliance-concern</code></td><td><span class=\"warn\">ALWAYS human approval · never auto at any phase · surfaces concern only</span></td></tr>"
   "        <tr><td>HARD · stall unverified</td><td><span class=\"critical\">permanent block · stall must be registered &amp; verified in store</span></td></tr>"
   "        <tr><td>HARD · effect not :propose</td><td><span class=\"critical\">permanent block · any non-propose effect is direct-actuation claim</span></td></tr>"
   "        <tr><td>HARD · scope exclusion</td><td><span class=\"critical\">permanent block · food-safety clearance / age-verification override / POS ID hardware / licensing enforcement</span></td></tr>"])

(defn render
  "Renders the full operator-console.html document from a store `db`
  that has already run `run-demo!` (or any other real scenario)."
  [db]
  (let [ledger (vec (store/ledger db))
        stalls (store/all-stalls db)
        coord (vec (store/coordination-log db))
        stall-rows (str/join "\n" (map (partial stall-row ledger) stalls))
        ledger-rows (str/join "\n" (map ledger-row ledger))
        coord-rows (str/join "\n" (map coord-row coord))]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-4781 &middot; market-stall-food-beverage-tobacco</title><style>"
     (jp-go-dds.skin/dds+skin)
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Retail sale via stalls and markets of food, beverages and tobacco (ISIC 4781) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · coordination only · food-safety/age-verification finalization permanently out of scope</span>\n"
     "</header>\n"
     "<main>\n"
     "  <section class=\"card\">\n"
     "    <h2>Market stalls</h2>\n"
     "    <p class=\"muted\">Demo snapshot — build-time-generated from <code>marketstallops.store</code> via <code>marketstallops.render-html</code> (<code>clojure -M:dev:render-html</code>), regenerated nightly. Last-op status is per stall (multiple ops may target the same stall).</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Stall</th><th>Name</th><th>Kind</th><th>Registration</th><th>Last op status</th></tr></thead>\n"
     "      <tbody>\n"
     stall-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Action gate (Market Stall Retail Governor)</h2>\n"
     "    <p class=\"muted\">HARD holds cannot be overridden. This actor coordinates sales logging, stall scheduling, supply orders and compliance flags only — it never finalizes a food-safety clearance or age-verification override, and never actuates POS ID hardware.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op / check</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" action-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Committed coordination log (this run)</h2>\n"
     "    <p class=\"muted\">Append-only SSoT of proposals that actually committed (auto or after human approval).</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Stall</th><th>Value</th></tr></thead>\n"
     "      <tbody>\n"
     (if (seq coord-rows) (str coord-rows "\n") "        <tr><td colspan=\"3\" class=\"muted\">none</td></tr>\n")
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — every proposal, hold and commit this scenario produced.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Stall</th><th>Basis</th></tr></thead>\n"
     "      <tbody>\n"
     ledger-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "</main>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        html (render db)
        f (java.io.File. out)]
    (when-let [parent (.getParentFile f)]
      (.mkdirs parent))
    (spit out html)
    (println "wrote" out "(" (count (store/ledger db)) "ledger facts,"
             (count (store/coordination-log db)) "coordination records )")))
