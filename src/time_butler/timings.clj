(ns time-butler.timings
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [time-butler.gtime :as gtime]
            [time-butler.loader :refer [json-slurp]]))

;; TODO external configuration
(def mappings
  "Maps a legacy step name to a new step name."
  {})

(defn remap-old-to-new
  "Convert old step names to new ones if they have a known mapping."
  [time]
  (if-let [remap (mappings (:name time))]
    (assoc time :name remap)
    time))

(defn from-manifest [dir filename]
  (->> (json-slurp dir filename [])
       (map (fn [step] (select-keys step [:name :real])))
       (map remap-old-to-new)
       (sort-by :name)
       (into [])))

(defn synthetic-time
  "Generate synthetic timing entries from aggregates.

  Construct synthetic `test` from the max time entry of `test.elm`, `test.rspec`, ..."
  [func name timings]
  (let [prefixed (filter #(str/starts-with? (:name %) name) timings)]
    (when (not-empty prefixed)
      {:name name :real (reduce func (map :real prefixed))})))

(defn synthesize
  "Create synthetic aggregates from a map of field, operation.

  Ie. (synthesize-timings timings {:compile max :compile.assets +})"
  [timings fields]
  (remove nil? (for [[field operation] fields]
                 (synthetic-time operation field timings))))

(defn impute-timings
  "normalizes resulting dataset so it is not jagged and has a value for every
  column across every build, by adding timing keys with value of 0.0 for missing
  keys"
  [builds]
  (let [all-timings (->> (mapcat :timings builds) (map :name) set)]
    (for [{:keys [timings] :as build} builds]
      (let [build-keys (set (map :name timings))
            missing-keys (set/difference all-timings build-keys)
            zero-elements (for [x missing-keys] {:name x :real 0.0})]
        (assoc build :timings (into timings zero-elements))))))

(defn key-conversion
  [k]
  (case k
    :started (comp inst-ms :started)
    :completed (comp inst-ms :completed)
    k))

(defn group-timings
  "generate a hash of keys to aggregate information across all builds. Flattens
  each of the timings with the provided `build-fields`."
  [build-fields builds]
  (-> {:revision-head (mapv (comp :head :revision) builds)}
      (into (for [key build-fields]
              {key (mapv (key-conversion key) builds)}))
      (into (for [[key values] (group-by :name (mapcat :timings builds))]
              ;; rescale timings to minutes
              {key (mapv #(gtime/to-minutes-float (% :real)) values)}))))

(defn- unique-metrics
  "Given a build returns the unique set of timing metrics"
  [metric-fn key-fn]
  (fn [build]
    (->> build
         metric-fn
         (map key-fn)
         (into #{}))))

(defn level
  "Given a list of builds and uniqueness function this normalizes the build data
  to ensure all timing metrics are present by adding any missing entries with the
  value zero."
  [metric-fn key-fn builds]
  (let [unique-timings (unique-metrics metric-fn key-fn)
        all-keys (set (mapcat unique-timings builds))]
    (map (fn [b] (let [known (unique-timings b)
                      unknown (set/difference all-keys known)]
                  (assoc-in b [:timings] (concat (:timings b) (map (fn [n] {:name n :real 0.0}) unknown))))) builds)))
