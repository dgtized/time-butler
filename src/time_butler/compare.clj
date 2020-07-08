(ns time-butler.compare
  (:require [clojure.core.matrix.dataset :refer [dataset]]
            [clojure.string :as str]
            [incanter.charts :as ch]
            [incanter.core :as ic]
            [incanter.stats :as is]
            [time-butler.chart.chart :as chart]
            [time-butler.steps :as steps]
            [time-butler.timings :as timings]))

(defn- branch-comparison-chart
  "Renders a stacked bar chart of branch to duration grouped by build steps."
  [[type data]]
  (ic/with-data (dataset (sort-by :step data))
    (ic/view
     (doto
         (ch/stacked-bar-chart :branch :mean
                               :title (format "%s Step Durations by Branch (Average)" (str/capitalize (name type)))
                               :x-label "Branch"
                               :y-label "Duration (minutes)"
                               :group-by :step
                               :legend true
                               :vertical false)
       chart/rotate-x))))

(defn compare-branches
  "Given a list of branch names, a time period and a quantile to aggregate by this
  will generate a bar-chart showing the duration for each build step by branch. Whilst
  this isn't included in the report it is used to validate experiments run as part of
  speeding up our CI.  The resulting chart is immediately displayed and not saved to
  disk."
  [builds]
  (->> builds
       (filter :success) ;; only include successful builds
       (timings/level :timings :name)
       (mapcat
        (fn [{:keys [branch timings]}]
          (for [timing timings]
            {:branch branch
             :step (:name timing)
             :duration (:real timing)})))
       (group-by #(select-keys % [:branch :step]))
       (map (fn [[k v]] (assoc k :mean (is/mean (map :duration v)))))
       (group-by (comp steps/step-type? :step))
       (map branch-comparison-chart)))

(comment
  ;; Branch comparisons (master against... preprecompile)
  (let [cfg {:builds {:branches ["master" "staging"] :period :day}}]
    (compare-branches (time-butler.build/filter-branches cfg true))))
