(ns time-butler.gtime
  (:require [java-time :as t]
            [clojure.edn :as edn]))

(defn to-minutes-float [seconds]
  (/ (quot (* 100 seconds) 60) 100))

(defn timestamp [timestamp]
  (when timestamp
    (t/java-date
     (* 1000
        (if (integer? timestamp)
          timestamp
          (edn/read-string timestamp))))))

(defn format-timestamp
  "format long timestamp to zulu timestamp string, ie 2018-07-20T06:56:20Z."
  [timestamp]
  (t/format (t/formatter :iso-instant) (t/instant timestamp)))

(defn duration
  "Computes duration from started to completed in seconds."
  [{:keys [started completed]}]
  (-> (t/duration (t/instant started) (t/instant completed))
      (t/as :millis)
      float
      (/ 1000)))

(defn by-age
  "From a `age` and `field` return a condition fn for anything with a `field`
  value younger than the limit."
  [age field]
  (let [ago (t/minus (t/instant) age)]
    (fn [thing]
      (t/after? (t/instant (get thing field)) ago))))

(defn by-age-range
  "Return a condition matching any times from a `field` value between `start` &
  `end`."
  [start end field]
  (fn [thing]
    (let [event-time (t/instant (get thing field))]
      (and (t/after? event-time start)
           (t/before? event-time end)))))
