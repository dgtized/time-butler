(ns time-butler.config
  "Provides access to site configuration stored in edn format."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]))

(defn load-config
  [filename]
  (log/infof "Loading configuration from %s" filename)
  (edn/read-string (slurp filename)))

(defn env
  []
  (load-config (io/resource "config.edn")))

(defn charts [cfg kind]
  (get-in cfg [kind :charts]))

;; TODO: specify output/ in config.edn?
(defn output-path
  ([] "output/")
  ([filename] (str (output-path) filename)))

(defn db
  ([config]
   (db config #(System/getenv %)))
  ([config getenv]
   (let [{:keys [bucket local-cache]} (:storage config)
         exception "Please specify s3 bucket for remote storage in config or BUTLER_BUCKET environment variable."]
     {:bucket (or (getenv "BUTLER_BUCKET") bucket
                  (throw (ex-info exception {})))
      :local-cache (or (getenv "BUTLER_CACHE") local-cache "jenkins-builds")})))

(defn rollbar-key
  []
  (System/getenv "BUTLER_ROLLBAR"))

(defn github-project
  [config]
  (-> config :project :github))

(defn synthetic-fields
  "Convert synthetic field mapping into a function to calculate the field.

  {\"foo\" :parallel}

  Will impute a series foo from all the passed in series with prefix foo using
  the function max."
  [{:keys [synthetic-fields]}]
  (let [field-summary {:parallel max
                       :sequential +}]
    (into {} (for [[field kind] synthetic-fields]
               (if-let [func (get field-summary kind)]
                 [field func])))))

(def version
  (memoize
   (fn []
     (if-let [[_ version] (re-find #"defproject time-butler \"([^\"]+)\""
                                   (slurp "project.clj"))]
       (do (log/infof "TimeButler Version %s" version)
           version)
       (do (log/error "Unable to parse time-butler.version from project.clj")
           "UNKNOWN")))))
