(ns time-butler.main
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [time-butler.analysis :as analysis]
            [time-butler.build :as build]
            [time-butler.chart.builds :as chart-builds]
            [time-butler.chart.chart :as chart]
            [time-butler.chart.deploys :as chart-deploys]
            [time-butler.chart.specs :as specs]
            [time-butler.config :as config]
            [time-butler.deploy :as deploy]
            [time-butler.rollbar :as rollbar]
            [time-butler.summary :as summary]
            [time-butler.view :as view]))

(defn build-ci-report
  "Generates the CI monitoring report using the branches and period in the chart titles
  and data from builds.
  This is used by the CI Monitoring Jenkins job which runs daily to give visibility into
  build time and reliability changes."
  [cfg builds deploys]
  (let [time-summary (summary/build-time (take-last 25 builds))
        layout
        (let [generated-for (view/generated-for cfg)
              links {"Home" "index.html"
                     "Slow Specs" "slow-specs.html"
                     "Nodes" "nodes.html"
                     "Flake Report" "flakes.html"
                     "Deploys" "deploy.html"}]
          (fn [title body] (view/layout title generated-for links body)))
        reports
        {"flakes.html"
         (layout "Flaky Builds & Specs"
                 (view/flake-report builds
                                    (config/github-project cfg)
                                    (chart/render chart-builds/build-results-by-day builds)))

         "timings.json"
         (json/write-str (summary/mean time-summary))

         "slow-specs.html"
         (let [builds-with-specs (build/spec-builds 25 builds)
               spec-charts
               (map #(chart/render % builds-with-specs)
                    [(specs/slowest-tests 15 :classname) (specs/slowest-tests 15 :spec)])]
           (layout (format "Slow specs from last %d builds" (count builds-with-specs))
                   (view/slow-specs spec-charts)))

         "nodes.html"
         (let [node-charts (map #(chart/render % builds)
                                [chart-builds/mean-duration-by-node
                                 chart-builds/outcome-frequency-by-node])
               duration-csv (analysis/generate-csv "node-duration.csv" builds
                                                   (get-in cfg [:builds :nodes-csv]))]
           (layout "Durations across Jenkins Nodes"
                   (view/nodes node-charts duration-csv)))

         "deploy.html"
         (layout "Deploy Health Report"
                 (view/deploy (chart/index (config/charts cfg :deploys)
                                           deploy/dataset
                                           deploys
                                           chart-deploys/duration-of-deploy-by-stack)
                              deploys))

         "index.html"
         (layout "CI Health Report"
                 (view/index (chart/index (config/charts cfg :builds)
                                          build/dataset
                                          builds
                                          chart-builds/duration-of-build-by-result)
                             time-summary))

         "time-butler.css" (slurp (io/resource "time-butler.css"))}]

    ;; TODO: announce index.html path for viewing the report on completion
    (doseq [[filename content] reports]
      (view/render (config/output-path filename) content))))

(defn generate-report
  [cfg {:keys [rollbar sync]}]
  (let [builds (build/filter-branches cfg sync)
        deploys (deploy/filter-branches cfg sync)]
    (build-ci-report cfg builds deploys)
    (when rollbar
      (rollbar/report-spec-failures
       (partial rollbar/tell! (config/rollbar-key) rollbar)
       builds))))

(comment
  ;; for local development repl usage
  (time
   (do
     (def cfg (assoc-in (config/env) [:builds :period] :week))
     (let [builds (build/filter-branches cfg false)
           deploys (deploy/filter-branches cfg false)]
       (build-ci-report cfg builds deploys)))))
