(ns time-butler.manifest
  "Parses build/deploy directories with a manifest.json."
  (:require [clojure.core.matrix.dataset :as matrix]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [me.raynes.fs :as fs]
            [time-butler.aws :as aws]
            [time-butler.config :as config]
            [time-butler.gtime :as gtime]
            [time-butler.loader :refer [json-slurp slurp-line-set]]
            [time-butler.timings :as timings]))

(defn sortable-node
  [name]
  (str/replace name #"\d+"
               (fn [x] (format "%02d" (edn/read-string x)))))

(defn- parse-name [dirname]
  ;; use if-let here as the directory may not match this pattern
  (if-let [[_ branch build-str _] (re-find #"(.*)-(\d+)-(\d+)$" dirname)]
    (let [build (edn/read-string build-str)]
      {:branch branch
       :build build
       :build-id [branch build]})))

(defn- load-manifest
  [dir base-info]
  (let [manifest (json-slurp dir "manifest.json" {})
        environment (get manifest :environment)
        step-timings (get-in manifest [:manifest :step_timings])]
    (assoc base-info
           :action (get-in manifest [:manifest :action])
           :directory dir
           :started (gtime/timestamp (get-in manifest [:timestamp :started]))
           :completed (gtime/timestamp (get-in manifest [:timestamp :completed]))
           :node (sortable-node (get environment :node_name))
           :revision (get manifest :revision)
           :environment (dissoc environment :node_name)
           :timings (timings/from-manifest dir step-timings)
           :assets (json-slurp dir (get-in manifest [:manifest :assets]) {})
           :failures (slurp-line-set dir "failed")
           :build-url (get manifest :build_url))))

(defn- post-process [synthetic-fields {:keys [failures timings] :as build}]
  (assoc build
         :duration (gtime/to-minutes-float (gtime/duration build))
         :success (empty? failures)
         :timings (concat timings (timings/synthesize timings synthetic-fields))))

(defn parse
  [synthetic-fields dir]
  (let [base-info (parse-name (fs/base-name dir))
        manifest (fs/exists? (fs/file dir "manifest.json"))]
    (if (and base-info manifest)
      (post-process synthetic-fields (load-manifest dir base-info))
      (log/warnf "Unable to parse build %s, excluding from results"
                 dir))))

(defn filter-builds
  "`config` is the top level config file contents. `report-type` is `:builds` or
  `:deploys` and corresponds to the top level configuration blobs in the config
  file."
  [config report-type sync]
  (let [{:keys [branches period]} (get config report-type)]
    ((if sync aws/sync-data aws/local-builds)
     (config/db config) branches :period period)))

(defn dataset
  "Calculate a dataset for a subset of grouped `fields`.

  Ensures missing values are set to 0."
  [fields]
  (comp matrix/dataset
        (partial timings/group-timings fields)
        timings/impute-timings))
