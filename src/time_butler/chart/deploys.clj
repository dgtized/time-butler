(ns time-butler.chart.deploys
  (:require [incanter.charts :as ch]
            [incanter.core :as ic]
            [time-butler.chart.chart :as chart]))

(def duration-of-deploy-by-stack
  {:title "Duration of Deploy by Stack"
   :filename "deploy-durations-by-stack"
   :graph-fn
   (fn [ds]
     (doto
         (ch/scatter-plot
          :started :duration :series-label "Production"
          :data (ic/$where {:stack :production} ds)
          :legend true)
       (ch/add-points :started :duration :series-label "Staging"
                      :data (ic/$where {:stack :staging} ds))
       (ch/add-points :started :duration :series-label "Demo"
                      :data (ic/$where {:stack :demo} ds))
       (ch/set-axis :y chart/duration-axis)
       (ch/set-axis :x chart/time-axis)))})
