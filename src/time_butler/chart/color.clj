(ns time-butler.chart.color
  "utility methods for setting dataset colors on charts"
  (:require [incanter.charts :as ch])
  (:import java.awt.Color
           org.jfree.chart.plot.XYPlot))

(defn- ^XYPlot get-plot [chart]
  (.getPlot chart))

(defn- dataset-at [chart idx]
  (-> chart get-plot (.getDataset idx)))

(defn- find-dataset-index [chart key]
  (let [dataset-count (-> chart (get-plot) .getDatasetCount)]
    (some
     (fn [idx] (if (= key (-> chart (dataset-at idx) (.getSeriesKey 0)))
                idx
                false))
     (range dataset-count))))

(defn- set-dataset-color [chart key color]
  (if-let [index (find-dataset-index chart key)]
    (ch/set-stroke-color chart color :dataset index)))

(defn apply-colors [chart color-mapping]
  (doall
   (for [[key color] color-mapping]
     (set-dataset-color chart key color))))

(def status-map
  {:success Color/green
   :flake Color/blue
   :failure Color/red})
