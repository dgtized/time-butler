(ns time-butler.view
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [hiccup.core :refer [html]]
            [time-butler.build :as build]
            [time-butler.config :as config]
            [time-butler.deploy :as deploy]
            [time-butler.gtime :refer [format-timestamp]]
            [time-butler.rollbar :as rollbar]
            [time-butler.sequential :as sequential]))

(defn- github-file-link [project file]
  ;; TODO: use revision or branch instead of master?
  (let [href (format "https://github.com/%s/blob/master/%s"
                     project file)]
    [:a {:href href} file]))

(defn jenkins-build-link
  [{:keys [build-id build-url started time failing-specs]}]
  (let [[branch build] build-id
        failures (count failing-specs)
        title (str branch "#" build
                   (when started
                     (str " at " (format-timestamp started)))
                   (when time
                     (format " in %3.2fs" time))
                   (when (and (not time) (pos? failures))
                     (str "\n" failures " failing specs")))]
    [:a {:href build-url
         :title title} build]))

(defn sequential-build-links
  "List of links to builds with consecutive sequences collapsed into ellipsed
  ranges, all separated by commas."
  [builds]
  (letfn [(in-seq?
            [[{[branch build] :build-id} {next :build-id}]]
            (= [branch (inc build)] next))
          (build-range [x]
            (let [s (first x)
                  e (last x)
                  size (count x)]
              [:span (jenkins-build-link s)
               [:span {:title (str size " builds")} "&hellip;"]
               (jenkins-build-link e)]))]
    ;; TODO might want to extract a visible prefix for each uniq branch name?
    (->> (sort-by :build-id builds)
         (sequential/collapse in-seq? build-range)
         (map (fn [x] (if (vector? x) x (jenkins-build-link x))))
         (interpose ", ")
         (into [:span]))))

(defn chart
  "Helper function to add chart definition to html"
  [{:keys [filename width height]}]
  [:div {:id filename}
   [:img
    {:src filename
     :border 0
     :vspace 15
     :width width
     :height height}]])

(defn generated-for
  [config]
  [:div
   [:p
    [:em (format "Build data covers the last %s from branch(es): "
                 (name (get-in config [:builds :period])))]
    (str/join ", " (get-in config [:builds :branches]))
    [:br]
    [:em (format "Deploy data covers the last %s from branch(es): "
                 (name (get-in config [:deploys :period])))]
    (str/join ", " (get-in config [:deploys :branches]))
    [:br]
    [:em "Last generated on: "]
    (format-timestamp (System/currentTimeMillis))
    [:em " with version: "]
    (config/version)]])

(defn- percent [x]
  (* 100 (float x)))

(defn- sample-percent
  [samples total]
  (format "%d&nbsp;(%3.2f%%)" samples (percent (/ samples total))))

(defn spec-report
  [{:keys [title caption project sample-size filtered-builds]}]
  (when-let [specs (->> filtered-builds
                        (remove :catastrophic)
                        build/failing-specs
                        seq)]
    [:section
     [:h2 title]
     [:table
      [:caption caption]
      [:thead
       [:tr
        [:th.col-width40 "Spec Name"]
        [:th.col-width25 "Source"]
        [:th.col-width5 {:title "Frequency of failures, in total build count and percent of sampled builds."
                         :align "right"} "Frequency"]
        [:th "Failing Builds"]]]
      [:tbody
       (for [{:keys [name file builds] :as spec} specs]
         [:tr
          [:td [:a {:href (rollbar/item-url spec)} name]]
          [:td (github-file-link project file)]
          [:td {:align "right"}
           (sample-percent (count builds) sample-size)]
          [:td (sequential-build-links builds)]])]]]))

(defn flake-summary [{:keys [total success failure flake]}]
  [:div
   [:p (format
        "For the last period there were a total of %d builds, %d were a
           success, %d failed, and %d were flakes."
        total success failure flake)]
   [:p "Builds are classified as flakes if it fails but an identical revision
          has been marked successful on a different build. All other failures
          are presumed to be from broken builds."]
   [:h3 (format
         "Success rate of %3.1f%% and a flake rate of %3.1f%%"
         (percent (/ success total))
         (percent (/ flake total)))]])

(defn failure-cause-distribution
  [builds]
  [:table
   [:caption
    "Distribution of failing builds grouped by the set of steps that failed."]
   [:thead [:tr
            [:th.col-width40 "Steps Failing Together"]
            [:th.col-width5 {:title "Frequency of group and percent of all sampled builds."
                             :align "right"}
             "Frequency"]
            [:th "Failing Builds"]]]
   [:tbody
    (for [[causes examples] (->> builds
                                 (group-by :failures)
                                 (sort-by (fn [[_ e]] (count e)))
                                 reverse)
          :when (seq causes)]
      [:tr
       [:td (map (fn [x] [:code x [:br]]) (sort causes))]
       [:td {:align "right"}
        (sample-percent (count examples) (count builds))]
       [:td (sequential-build-links examples)]])]])

(defn flake-report [builds project flake-chart]
  [:div
   [:h2 "Summary"]
   (flake-summary (build/statistics builds))
   (chart flake-chart)
   (let [{:keys [failure flake]} (group-by :build-grade builds)]
     [:div
      [:p [:b "Calculated from builds: "] (sequential-build-links builds)]
      [:p [:b "Failing Builds: "] (sequential-build-links failure)]
      [:p [:b "Flaking Builds: "] (sequential-build-links flake)]
      [:p [:b "Catastrophic Builds: "]
       (sequential-build-links (filter :catastrophic builds))]
      [:p (format
           "Builds with %d or more spec failures are considered catastrophic and
            excluded from the lists of specs below. Catastrophic builds are more
            likely to indicate that the failures are dependent events with a
            shared cause, such as DST change or a common factory, and should not
            be considered independent events."
           build/catastrophic-threshold)]
      ;; TODO: conslidate into a single "no flaking tests" message if none are found
      (spec-report
       {:title "Flaking specs"
        :caption "Failing specs on builds classified as a flake, excluding catastrophic builds."
        :project project
        :sample-size (count builds)
        :filtered-builds flake})
      (spec-report
       {:title "Failing specs"
        :caption "Failing specs on builds classified as a flake or a failure, excluding catastrophic builds."
        :project project
        :sample-size (count builds)
        :filtered-builds builds})])
   [:h2 "Grouped Causes of Failure for All Builds"]
   [:p "Identify builds failing for reasons other than spec failures"]
   (failure-cause-distribution builds)])

(defn summary-stat [stat]
  [:td {:align "right"
        :title (format "%.2f minutes" (/ stat 60))}
   (format "%.3f" stat)])

(defn index [charts time-summary]
  [:div (map chart charts)
   [:h2 "Step times over last 25 builds"]
   [:a {:href "timings.json"} "(timings.json)"]
   [:table.fixed-width
    [:thead
     [:tr
      [:th {:align "left"} "Step"]
      [:th {:align "right"} "Mean (s)"]
      [:th {:align "right"} "Median (s)"]
      [:th {:align "right"} "90th Percentile (s)"]]]
    [:tbody
     (for [[step stats] (sort-by first time-summary)]
       [:tr
        [:td step]
        (summary-stat (:mean stats))
        (summary-stat (:median stats))
        (summary-stat (:percentile-90 stats))])]]])

(defn deploy [charts deploys]
  [:div
   [:h2 "Metrics over last 7 days"]
   [:table.fixed-width
    [:thead [:tr [:th "Stack"] [:th "Metrics"]]]
    [:tbody
     (for [[stack metrics] (deploy/metrics deploys)]
       [:tr [:td stack] [:td [:code (str metrics)]]])]]
   [:h2 "Charts"]
   (map chart charts)])

(defn slow-specs [charts]
  [:div
   [:p "Spec times are averaged over builds."]
   [:div (map chart charts)]])

(defn nodes [charts duration-csv]
  [:div (map chart charts)
   [:a {:href duration-csv} "(node-durations.csv)"]])

(defn layout [title generated-for links fragment]
  (html
   [:html
    [:head [:title title]
     [:link {:href "time-butler.css" :rel "stylesheet" :type "text/css"}]]
    [:body
     [:h1 title]
     generated-for
     [:p (interpose
          " - "
          (for [[name href] links]
            [:a {:href href} name]))]
     [:div#container fragment]]]))

(defn render
  [filename content]
  (log/info "Render Page: " filename)
  (spit filename content))

(comment
  ;; hack: shadowing builds var allows evaluation of view segments
  (def builds time-butler.build/builds)
  (def time-summary time-butler.summary/time-summary)
  (def deploys time-butler.deploy/deploys)

  (defn example [filename title body]
    (render (time-butler.config/output-path filename)
            (layout title
                    (generated-for (time-butler.config/env)) {}
                    body))
    body)

  (example "flakes.html"
           "Flaky Builds & Specs"
           (flake-report builds
                         (time-butler.config/github-project (time-butler.config/env))
                         {:filename "build-results-by-day.png"
                          :width 1000 :height 300}))
  (example "index.html"
           "index"
           (index [] time-summary)))
