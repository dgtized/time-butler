(ns time-butler.chart.specs
  (:require [clojure.core.matrix.dataset :refer [dataset]]
            [incanter.charts :as ch]
            [incanter.stats :as is]
            [time-butler.chart.chart :as chart]
            [time-butler.timings :as timings]))

(defn- ordered-groupings [key data]
  (->> data
       (group-by key)
       (map (fn [[k, v]] [k (is/mean (map :time v))]))
       (sort-by last)
       reverse
       (map first)))

(defn- format-specs [build]
  (let [started (inst-ms (:started build))]
    (into [] (map (fn [spec]
                    {:started started
                     :classname (:classname spec)
                     :file (:file spec)
                     :name (:name spec)
                     :spec (format "%s:%s" (:classname spec) (:name spec))
                     :time (/ (:time spec) 60)})
                  (:specs build)))))

;; FIXME: has problem with an empty set from `builds-withs-specs`
(defn slowest-tests [n grouping-fn]
  {:title (format "Slowest Tests (grouped by %s)" (name grouping-fn))
   :filename (format "slowest-tests-%s" (name grouping-fn))
   :graph-fn
   (fn [builds]
     (let [chart-data
           (mapcat format-specs (timings/level :specs grouping-fn builds))
           top-n-groupings
           (set (take n (ordered-groupings grouping-fn chart-data)))
           top-n-data
           (filter (fn [build] (contains? top-n-groupings (grouping-fn build)))
                   chart-data)
           dataset (->> top-n-data
                        (group-by (fn [x] [(:started x) (grouping-fn x)]))
                        (map (fn [[k, v]] [k, (reduce + (map :time v))]))
                        (map (fn [[[s, c], t]] {:started s :grouping c :time t}))
                        (sort-by :time)
                        reverse
                        dataset)]
       (doto
           (ch/time-series-plot :started :time
                                :group-by :grouping
                                :data dataset
                                :legend true)
         (ch/set-axis :y chart/duration-axis)
         (ch/set-axis :x chart/time-axis))))})
