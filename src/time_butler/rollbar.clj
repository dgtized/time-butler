(ns time-butler.rollbar
  (:require [circleci.rollcage.json :as json]
            [clj-http.client :refer [post]]
            [clj-uuid :as uuid]
            [clojure.string :as string]
            [clojure.tools.logging :as logging]
            [time-butler.build :as build]
            [time-butler.config :as config]
            [time-butler.gtime :as gtime]))

;; stolen from rollcage because they are annoyingly private
(def ^:private endpoint "https://api.rollbar.com/api/1/item/")
(def ^:private http-conn-timeout 3000)
(def ^:private http-socket-timeout 3000)

(defn- send-message-null
  [{data :data}]
  (logging/info data "No Rollbar token configured. Not reporting exception.")
  {:err 0
   :skipped true
   :result {:uuid (str (:uuid data))}})

(defn- send-message-http
  [{data :data :as item}]
  (logging/info data "Sending message to Rollbar")
  (let [result (post endpoint
                     {:body (json/encode item)
                      :conn-timeout http-conn-timeout
                      :socket-timeout http-socket-timeout
                      :content-type :json})]
    (json/decode (:body result))))

(defn tell!
  "Tell rollbar about all our naughty flakes."
  [access-token environment item]
  (try
    ((if (string/blank? access-token)
       send-message-null
       send-message-http)
     {:data (assoc item :environment environment)
      :access-token access-token})
    (catch Exception e
      {:err 1
       :exception e
       :message (.getMessage e)})))

(defn spec->uuid
  "Reduce a `spec` failure into a unique, reproducible uuid for the given build."
  [spec]
  (uuid/v5 uuid/+namespace-url+
           (select-keys spec [:file :classname :name :build-id])))

(defn- spec->rollbar-item
  "Build the `:body` of a rollbar-item occurrence from a `spec`.

  See https://docs.rollbar.com/reference#items"
  [{:keys [started node classname file name time build-url build-grade]
    :as spec}]
  {:notifier {:name "TimeButler" :version (config/version)}
   :level "warning"
   ;; Ignored: https://docs.rollbar.com/docs/timestamps
   ;; referenced in RQL as "customer_timestamp"
   :timestamp (inst-ms started)
   ;; This should technically be both :head/:master since both are necessary,
   ;; however for the majority of builds processed :head & :master will be
   ;; the same since they are both master. For those builds on branches ahead
   ;; of master this is incorrect but good enough.
   :code_version (get-in spec [:revision :head])
   :server {:host node}
   ;; the associated build is kinda like the request
   :request {:url build-url
             :build-grade build-grade}
   :uuid (spec->uuid spec)
   ;; :context
   ;; :fingerprint
   ;; :title
   :body
   {:message
    {:classname classname
     :file file
     :body name
     :duration time
     :timestamp (gtime/format-timestamp started)}}})

(defn report-spec
  [specs]
  ;; Order by started to keep items in order
  ;; NOTE: pmap removes ordering, but this might be reason to drop pmap
  (->> specs
       (sort-by :started)
       (map spec->rollbar-item)))

(defn trim-last-failures
  "Drop failing builds with matching revision to the last, allowing failures to
  converge to flakes before reporting the build-grade of the spec to rollbar."
  [builds]
  (let [last-revision (:revision (last builds))]
    (->> builds
         reverse
         (drop-while (fn [{:keys [build-grade revision]}]
                       (and (= build-grade :failure)
                            (= revision last-revision))))
         reverse)))

(defn report-spec-failures
  "Report failing specs annotated by build to a rollbar reporter in parallel."
  [reporter builds]
  (let [specs (->> builds trim-last-failures build/failing-specs-with-build)]
    (logging/infof "Reporting %d failures to Rollbar" (count specs))
    (time
     (dorun
      (pmap reporter (report-spec specs))))))

(defn item-url
  "url for rollbar item"
  [spec]
  (let [first-build-id (-> spec :builds first :build-id)]
    (str "https://rollbar.com/item/uuid/?uuid="
         (spec->uuid (assoc spec :build-id first-build-id)))))
