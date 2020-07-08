(ns time-butler.analysis
  (:require [clojure.core.matrix.dataset :refer [column-names]]
            [incanter.charts :as ch]
            [incanter.core :as ic :refer [$]]
            [incanter.io];; defmethod save for dataset type
            [time-butler.build :as build]
            [time-butler.config :as config]))

(defn generate-csv [filename builds columns]
  (let [ds (build/dataset builds)]
    (ic/save (ic/sel ds :cols columns)
             (config/output-path filename)
             :header columns)
    filename))

(comment
  (do
    (def builds build/builds)
    (def ds (build/dataset builds)))

  (column-names ds)
  (ic/view ds)
  (ic/view ($ [:build "test.elm" "test.ruby-specs" "compile.assets"] ds))
  (ic/view ($ [:build :revision-head :build-grade :failures] ds))

  ;; histograms for each timing distribution
  (doto (ch/histogram ($ "test.elm" ds) :legend true)
    (ic/view :width 1600 :height 1200))
  (doto (ch/histogram ($ "test.ruby-specs" ds) :legend true)
    (ic/view :width 1600 :height 1200))
  (doto (ch/histogram ($ "compile.assets" ds) :legend true)
    (ic/view :width 1600 :height 1200))

  ;; xy-plot comparing build times of top 3 builds
  (let [r ($ :build ds)]
    (doto (ch/xy-plot r ($ :duration ds) :series-label "Total Duration"
                      :legend true
                      :title "Compare Durations of Stages on Master"
                      :x-label "Build"
                      :y-label "Minutes")
      (ch/set-stroke :width 2 :series 0)
      (ch/set-stroke-color (java.awt.Color/decode "#000000") :series 0)
      (ch/add-lines r ($ "compile.assets" ds) :series-label "compile.assets")
      (ch/add-lines r ($ "test.ruby-specs" ds) :series-label "test.ruby-specs")
      (ch/add-lines r ($ "test.elm" ds) :series-label "test.elm")
      (ic/save "compare-parallel-builds.png" :width 800 :height 600)
      (ic/view :width 1600 :height 1200)))

  (ic/with-data ds
    (doto (ch/box-plot "test.elm" :legend true)
      (ch/add-box-plot "test.ruby-specs")
      (ch/add-box-plot "compile.assets")
      (ic/save "compare-parallel-boxplot.png" :width 800 :height 600)
      (ic/view :width 1600 :height 1200)))

  (ic/with-data ds
    (doto (ch/xy-plot :build :duration
                      :series-label "Duration"
                      :legend true
                      :title "Build Durations"
                      :x-label "Build"
                      :y-label "Minutes")
      (ic/save "compare-parallel-builds.png" :width 800 :height 600)
      (ic/view :width 1600 :height 1200)))

  (ic/with-data (as-> ds x (ic/sel x :cols [:node :duration])
                      (ic/$rollup :mean :duration :node x)
                      (ic/$order :node :asc x))
    (doto (ch/bar-chart :node :duration
                        :title "Mean Duration by Jenkins Node"
                        :y-label "Minutes"
                        :x-label "Jenkins Node")
      (ch/set-stroke-color (java.awt.Color/decode "#333399") :series 0)
      (ic/view :width 1600 :height 1200)))

  (ic/with-data (as-> ds x
                  (ic/$rollup :count :build [:node :build-grade] x)
                  (ic/$order :node :asc x))
    (doto (ch/stacked-bar-chart :node :build
                                :title "Outcome Frequency by Jenkins Node"
                                :group-by :build-grade
                                :legend true
                                :y-label "Count"
                                :x-label "Jenkins Node")
      (ic/view :width 1600 :height 1200)))

  (generate-csv (time-butler.config/output-path "node-duration.csv") builds
                [:build :node :duration
                 "test.elm" "test.ruby-specs" "compile.assets"]))
