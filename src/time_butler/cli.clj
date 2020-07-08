(ns time-butler.cli
  (:require [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.tools.logging :as log]
            [me.raynes.fs :as fs]
            [time-butler.config :as config]
            [time-butler.main :as main])
  (:gen-class))

(defn- ensure-folder [^String required-path]
  (if (or (fs/directory? "output") (fs/mkdirs required-path))
    (do
      (log/info "Successfully created output folder: " (str (fs/normalized required-path)))
      true)
    (do
      (log/error "Failed to create output folder: " required-path)
      false)))

(defn- ensure-folders [required-paths]
  (every? identity (map ensure-folder required-paths)))

(def cli-options
  [["-c" "--config FILE.EDN"
    "Specify configuration file"
    :id :config-file
    :parse-fn fs/file]
   [nil "--[no-]sync"
    "Synchronize data from s3 bucket or reuse local copy"
    :default true]
   ["-r" "--rollbar ENVIRONMENT"
    "Enable reporting spec failures to rollbar ENVIRONMENT"
    :default nil]
   ["-h" "--help"]])

(defn- usage [options-summary]
  (->> ["time-butler reports on CI time and error rates"
        ""
        "Usage: time-butler [options]"
        ""
        "Options:"
        options-summary
        ""]
       (str/join \newline)))

(defn- missing-required? [opts]
  (not-every? opts #{:config-file}))

(defn- process-args [args]
  (let [{:keys [options summary errors]} (parse-opts args cli-options)]
    (cond (or (:help options) (missing-required? options))
          {:exit-message (usage summary) :ok? true}
          errors
          {:exit-message
           (str "The follow errors occured while parsing your command:"
                (str/join \newline errors))}
          :else
          {:options options})))

(defn -main [& args]
  (let [{:keys [options exit-message ok?]} (process-args args)]
    (when exit-message
      (println exit-message)
      (System/exit (if ok? 0 1)))
    (try
      (let [cfg (config/load-config (:config-file options))]
        (if (ensure-folders [(config/output-path) (:local-cache (config/db cfg))])
          (main/generate-report cfg options)
          (System/exit 1)))
      (finally
        ;; Avoid 1 minute shutdown delay
        ;; See http://dev.clojure.org/jira/browse/CLJ-124 for more detail
        (prn "Shutting down agents")
        (shutdown-agents)))))
