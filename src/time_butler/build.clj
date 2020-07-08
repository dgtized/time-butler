(ns time-butler.build
  "Parse and process jenkins builds"
  (:require [clojure.set :as set]
            [java-time :as t]
            [time-butler.config :as config]
            [time-butler.manifest :as manifest]
            [time-butler.rspec :as rspec]))

(defn specs [dirname]
  (let [all-spec-xml (rspec/load-spec-xml dirname)]
    {:specs (rspec/all-specs all-spec-xml)
     ;; clearer if nested under specs?
     :failing-specs (rspec/all-spec-failures all-spec-xml)}))

(defn add-specs-for-failing [build]
  (if (:success build)
    build
    (merge (specs (:directory build)) build)))

(defn successful-revisions
  "Generates a map of revision to a union of builds that succeeded.
  Returns an empty set if no builds were successful."
  [builds]
  (->> (filter :success builds)
       (map (fn [{:keys [revision build-id]}]
              {revision #{build-id}}))
       (apply merge-with set/union {})))

(defn annotate-flakes
  "Annotate builds with :flakes, :success-in reports builds with matching
  revision that passed, and :flaky lists a probable flake."
  [builds]
  (let [good-revisions (successful-revisions builds)]
    (for [{:keys [revision success] :as build} builds
          :let [success-in (good-revisions revision)]]
      (assoc build :flakes
             {:success-in success-in ;; might be itself
              :flaky (and (not success)
                          (not (empty? success-in)))}))))

(defn classify-build [build]
  (cond (:success build) :success
        (get-in build [:flakes :flaky]) :flake
        :else :failure))

(defn statistics [builds]
  (let [freqs (frequencies (map :build-grade builds))]
    (merge
     {:success 0 :failure 0 :flake 0}
     (assoc freqs :total (reduce + (vals freqs))))))

(defn statistics-by [period builds]
  (->>
   (for [[floor-day period-builds]
         (group-by #(t/truncate-to (t/offset-date-time (t/instant (:started %)) "UTC") period) builds)]
     (assoc (statistics period-builds)
            :date (t/format "d-MMM" floor-day)
            :period-floor floor-day))
   (sort-by :period-floor)))

;; TODO: externalize to config.edn
(def catastrophic-threshold
  "Threshold for classifying multiple spec failures as dependent events."
  10)

(defn process-builds [config builds]
  (->> builds
       (map (partial manifest/parse (config/synthetic-fields config)))
       (remove nil?)
       (sort-by :completed)
       (map add-specs-for-failing)
       annotate-flakes
       (map (fn [{:keys [failing-specs] :as build}]
              (assoc build
                     :build-grade
                     (classify-build build)
                     :catastrophic
                     (> (count failing-specs) catastrophic-threshold))))
       (into [])))

(defn failing-specs-annotated-from-build
  "List of `failing-specs` in a `build` annotated with `fields` from the build."
  [fields]
  (fn [{:keys [failing-specs] :as build}]
    (for [spec failing-specs]
      (merge (select-keys spec [:file :classname :name :time])
             (select-keys build fields)))))

(defn failing-specs
  "Extracts a list of failing specs annotated with the list of builds they failed
  on. "
  [builds]
  (->> builds
       ;; annotate each failing spec with list of build-id, started
       (mapcat (failing-specs-annotated-from-build
                [:build-id :build-url :started]))
       (group-by :name)
       ;; construct canonical spec description listing all builds, times, and start time
       (map (fn [[name values]]
              (assoc (select-keys (first values) [:name :classname :file])
                     :builds
                     (map #(select-keys % [:build-id :build-url :time :started]) values))))

       ;; sort by descending number of failing builds
       (sort-by #(count (:builds %)))
       reverse))

(defn failing-specs-with-build
  "Failing specs annotated with the build they failed on excluding catastrophic."
  [builds]
  (->> builds
       (remove :catastrophic)
       (mapcat (failing-specs-annotated-from-build
                [:build-id :started :revision :node
                 :build-url :build-grade]))))

(defn flaking-specs
  "Extracts a list of failing specs from flakey builds annotated with the list of
  builds they failed on. "
  [builds]
  (->> builds
       (filter #(= (:build-grade %) :flake))
       failing-specs))

(defn filter-branches
  [config sync]
  (process-builds config
                  (manifest/filter-builds config :builds sync)))

(def dataset
  (manifest/dataset [:branch :build :node
                     :completed :started :duration
                     :failures :success :build-grade]))

(defn spec-builds
  "last N builds annotated with individual spec information."
  [n builds]
  (for [{:keys [directory] :as build}
        (take-last n builds)]
    (merge build (specs directory))))

(comment
  (time
   (def builds (filter-branches (time-butler.config/env) false)))
  (count (filter #(seq (% :failing-specs)) builds))
  (flaking-specs builds)
  (statistics builds)
  (statistics-by :days builds))

