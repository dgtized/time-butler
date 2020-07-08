(ns time-butler.deploy
  "Parse and process deploys"
  (:require [incanter.core :as ic]
            [incanter.stats :as is]
            [java-time :as t]
            [time-butler.config :as config]
            [time-butler.gtime :as gtime]
            [time-butler.manifest :as manifest]))

;; TODO: use defmulti on process :deploys
(defn process-deploys [config deploys]
  (->> deploys
       (map (partial manifest/parse (config/synthetic-fields config)))
       (remove nil?)
       (sort-by :completed)
       (map #(assoc % :stack (keyword (get-in % [:environment :stack]))))
       (into [])))

(defn filter-branches
  [config sync]
  (process-deploys config
                   (manifest/filter-builds config :deploys sync)))

(def dataset
  (manifest/dataset [:build-id :stack
                     :started :completed :duration
                     :failures :success]))

(defn force-coll
  "Force argument to convert to a vector if not a sequence."
  [coll]
  (if (seq? coll)
    coll
    (conj [] coll)))

(defn metrics [deploys]
  (let [ds (dataset (filter (gtime/by-age (t/weeks 1) :started) deploys))]
    (into {}
          (for [[{stack :stack} stack-ds] (ic/$group-by :stack ds)
                ;; ic/$ on singular matrices returns a double, not a vector,
                ;; which breaks median/mean calculation below.
                :let [durations (force-coll (ic/$ :duration stack-ds))]]
            {stack {:durations durations
                    :mean (is/mean durations)
                    :median (is/median durations) }}))))

(comment
  (time (def deploys
          (filter-branches {:deploys {:branches ["deploy-staging"
                                                 "deploy-production"]
                                      :period :fortnight}}
                           true)))
  (first deploys)
  (dataset deploys)
  (metrics deploys))
