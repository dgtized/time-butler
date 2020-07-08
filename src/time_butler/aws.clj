(ns time-butler.aws
  "This provides helpers to retrieve data from s3 for analysis.
  See sync-data for further details."
  (:require [clojure.edn :as edn]
            [clojure.string :refer [blank?]]
            [clojure.tools.logging :as log]
            [java-time :as t]
            [me.raynes.fs :as fs]
            [time-butler.gtime :as gtime]
            [time-butler.s3 :as s3]))

(defn- parse-entry
  "Parses local and remote directory listing entries."
  [name]
  (if-let [[_ branch build timestamp]
           (re-find #"^(.*)-(\d+)-(\d+)/?$" name)]
    {:branch branch,
     :build (edn/read-string build),
     :timestamp (t/java-date (* 1000 (edn/read-string timestamp)))}))

(defn- branches-predicate [branches]
  (if (empty? branches)
    (fn blank-branches [{branch :branch}]
      (not (blank? branch)))
    (fn blank-branches [{branch :branch}]
      (and
        (not (blank? branch))
        (some #(= branch %) branches)))))

(defn- resolve-period [period]
  (case period
    :hour (t/hours 1)
    :two_hours (t/hours 2)
    :day (t/days 1)
    :week (t/weeks 1)
    :fortnight (t/weeks 2)
    :month (t/months 1)
    (t/days 1)))

(defn- list-builds [in branches & {:keys [period] :or {period :day}}]
  (->> in
       sort
       (map parse-entry)
       (remove nil?)
       (filter (branches-predicate branches))
       (filter (gtime/by-age (resolve-period period) :timestamp))))

(defn- build-name [{:keys [branch build timestamp]}]
  (format "%s-%d-%d" branch build
          (t/as (t/instant timestamp) :instant-seconds)))

(defn- build-file [local-cache build]
  (fs/file (format "%s/%s" local-cache (build-name build))))

(defn remote-builds
  "Return names of remote builds matching branch and period."
  [{:keys [bucket]} branches & {:keys [period] :or {period :day}}]
  (list-builds (s3/aws-list {:bucket-name bucket :delimiter "/"})
               branches :period period))


(defn local-builds
  "directeries of local build data matching branch and period."
  [{:keys [local-cache]} branches & {:keys [period] :or {period :day}}]
  (map (partial build-file local-cache)
       (list-builds (map fs/base-name (fs/list-dir local-cache))
                    branches :period period)))

(defn sync-data
  "Sync build timing data from s3 for the specified branches and time period.
  This uses the timestamp on the folder to identify builds falling within the
  requested period.  After filtering that list a sync request is made to retreive
  the data.

  Returns directories of the builds retrieved.

  Example usage:

  (sync-data (config/db cfg) [\"master\"] :period :day)
  "
  [{:keys [bucket local-cache] :as db} branches & {:keys [period] :or {period :day}}]
  (let [builds (remote-builds db branches :period period)]
    (log/infof "Syncing: %d builds" (count builds))
    (dorun
     (pmap (fn [build]
             (let [start (System/nanoTime)
                   build-dir (str (build-name build) "/")]
               (s3/sync-directory bucket build-dir (fs/file local-cache))
               (log/infof "Syncing: %s [%.0f msecs]"
                          build-dir
                          (/ (double (- (System/nanoTime) start))
                             1000000.0))))
           builds))
    (log/info "Syncing: Completed")
    (map (partial build-file local-cache) builds)))

(comment
  ;; Example Usages:
  (def db (time-butler.config/db (time-butler.config/env)))

  ;; Sync the last month of master build data
  (sync-data db ["master"] :period :month)

  ;; Sync the last week of master build data
  (sync-data db ["master"] :period :week)

  ;; Sync the last hour of master build data
  (sync-data db ["master"] :period :hour)

  ;; Sync the last hour of all build data
  (sync-data db [] :period :two_hours)

  (remote-builds db ["master"] :period :day)
  (local-builds db ["master"] :period :day))

