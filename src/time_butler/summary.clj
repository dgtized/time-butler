(ns time-butler.summary
  (:require [time-butler.gtime :as gtime]
            [incanter.stats :as is]))

(defn build-stats [times]
  {:mean (is/mean times)
   :median (is/median times)
   :percentile-90 (is/quantile times :probs 0.9)})

(defn timing-map [build]
  (->> build
       :timings
       (map (fn [{:keys [name real]}] {name [real]}))
       (into {"total" [(gtime/duration build)]})))

(defn build-time [builds]
  (->> builds
       (map timing-map)
       (apply merge-with into)
       (map (fn [[k v]] {k, (build-stats v)}))
       (into {})))

(defn mean [time-summary]
  (->> time-summary
       (map (fn [[k, v]] [k (:mean v)]))
       (into {})))

(comment
  ;; calculate average/max build times over the last 25 builds
  (def time-summary
    (->> time-butler.build/builds (take-last 25) build-time)))
