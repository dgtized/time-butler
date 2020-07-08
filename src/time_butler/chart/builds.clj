(ns time-butler.chart.builds
  (:require [clojure.core.matrix.dataset :as matrix]
            [incanter.charts :as ch]
            [incanter.core :as ic]
            [time-butler.build :as build]
            [time-butler.chart.chart :as chart]
            [time-butler.chart.color :as color])
  (:import java.awt.Color))

(def duration-of-build-by-result
  "Graph duration of build by success/flake/failure"
  {:title "Duration of Build by Success/Flake/Failure"
   :filename "pass-fail-build-durations"
   :graph-fn
   (fn [ds]
     (doto
         (ch/scatter-plot
          :started :duration :series-label "Failure"
          :data (ic/$where {:build-grade :failure} ds)
          :legend true)
       (ch/add-points :started :duration :series-label "Flake"
                      :data (ic/$where {:build-grade :flake} ds))
       (ch/add-points :started :duration :series-label "Success"
                      :data (ic/$where {:build-grade :success} ds))
       (ch/set-axis :y chart/duration-axis)
       (ch/set-axis :x chart/time-axis)))})

(def mean-duration-by-node
  "Graph mean duration of builds by node"
  {:title "Mean Duration by Jenkins Node"
   :filename "mean-duration-by-node"
   :graph-fn
   (fn [builds]
     (ic/with-data (as-> (build/dataset builds) x
                     (ic/sel x :cols [:node :duration])
                     (ic/$rollup :mean :duration :node x)
                     (ic/$order :node :asc x))
       (doto (ch/bar-chart :node :duration
                           :y-label "Minutes"
                           :x-label "Jenkins Node")
         (ch/set-stroke-color (Color/decode "#333399") :series 0))))})

(def outcome-frequency-by-node
  "Graph frequency of each build result by node"
  {:title "Outcome Frequency by Jenkins Node"
   :filename "failures-by-node"
   :graph-fn
   (fn [builds]
     (let [ds (->> (build/dataset builds)
                   (ic/$rollup :count :build [:node :build-grade])
                   (ic/$order :node :asc))]
       (-> (ch/stacked-bar-chart :node :build
                                 :data (ic/$where {:build-grade :success} ds)
                                 :series-label "Success"
                                 :legend true
                                 :y-label "Builds"
                                 :x-label "Jenkins Node")
           (ch/set-stroke-color (color/status-map :success) :series 0)
           (ch/add-categories :node :build :series-label "Flake"
                              :data (ic/$where {:build-grade :flake} ds))
           (ch/set-stroke-color (color/status-map :flake) :series 1)
           (ch/add-categories :node :build :series-label "Failure"
                              :data (ic/$where {:build-grade :failure} ds))
           (ch/set-stroke-color (color/status-map :failure) :series 2))))})

(def build-results-by-day
  "Graph build success/flake/failure by day"
  {:title "Sum of build results by day"
   :filename "build-results-by-day"
   :height 300
   :graph-fn
   (fn [builds]
     (ic/with-data
       (matrix/dataset [:date :total :success :failure :flake]
                       (build/statistics-by :days builds))
       (-> (ch/stacked-bar-chart :date :success
                                 :x-label "Date"
                                 :y-label "Builds"
                                 :series-label "Success"
                                 :legend true)
           (ch/set-stroke-color (color/status-map :success) :series 0)
           (ch/add-categories :date :flake :series-label "Flake")
           (ch/set-stroke-color (color/status-map :flake) :series 1)
           (ch/add-categories :date :failure :series-label "Failure")
           (ch/set-stroke-color (color/status-map :failure) :series 2))))})

(comment
  (def builds build/builds)
  (chart/render build-results-by-day builds)
  (chart/render outcome-frequency-by-node builds)
  (chart/render duration-of-build-by-result builds))
