(ns time-butler.chart.chart
  (:require [clojure.core.matrix.dataset :as matrix]
            [clojure.set :as set]
            [clojure.tools.logging :as log]
            [incanter.charts :as ch]
            [incanter.core :as ic :refer [$]]
            [time-butler.config :as config])
  (:import java.awt.Color
           [org.jfree.chart.axis CategoryLabelPositions DateAxis NumberAxis]))

;; shared axis definitions
(def time-axis (DateAxis. "Time of Build"))
(def duration-axis (NumberAxis. "Duration (Minutes)"))

(defn rotate-x
  "Rotate the labels on the X-axis of a bar-chart. To use:
    (def chart (bar-chart :data data))
    (rotate-x chart)
    (view chart)"
  ([chart]
   (-> chart
       .getPlot
       .getDomainAxis
       (.setCategoryLabelPositions CategoryLabelPositions/UP_90))
   chart))

(defn time-series
  "Generate a time-series chart from a reduced vector of named timings and a
  dataset."
  [series ds]
  (let [column-names (set (matrix/column-names ds))
        [head & remaining] (filter column-names series)
        chart (ch/time-series-plot
               :started head
               :series-label head
               :data ds
               :legend true)]
    (doseq [key remaining]
      (ch/add-lines chart ($ :started ds) ($ key ds)
                    :series-label key))
    (doto chart
      (ch/set-stroke :width 2 :series 0)
      (ch/set-stroke-color (Color/decode "#000000") :series 0)
      (ch/set-axis :y duration-axis)
      (ch/set-axis :x time-axis))))

(defn render
  "Renders a chart map to image as side-effect and return a map of filename, width,
  height."
  [{:keys [title filename graph-fn] :as chart} builds]
  (let [image-name (format "%s.png" filename)
        image-path (config/output-path image-name)
        width (get chart :width 1000)
        height (get chart :height 600)]
    (log/info "Render Chart: " image-path)
    (doto (graph-fn builds)
      (ch/set-background-alpha 0.5)
      (ch/set-title title)
      (ic/save image-path :width width :height height))
    {:filename image-name :width width :height height}))

(defn remainder
  "Chart the remaining time series that are not included in `charts`"
  [builds charts]
  (let [all (map :name (mapcat :timings builds))
        charted (mapcat :series charts)]
    {:title "New Build Steps/Timings" :filename "uncharted-timings"
     :series (into [] (set/difference (set all) (set charted)))}))

(defn index
  "Render a chart for every :series specified in charts, and any missing series."
  [charts ds-fn builds & other-charts]
  (let [ds (ds-fn builds)
        graphs
        (for [{:keys [series] :as chart}
              (conj charts (remainder builds charts))
              :when (seq series)]
          (assoc chart :graph-fn (partial time-series series)))]
    (map #(render % ds) (concat other-charts graphs))))
