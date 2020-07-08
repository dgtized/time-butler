(ns time-butler.summary-test
  (:require [time-butler.summary :as summary]
            [clojure.test :refer :all]))

(deftest timing-from-build
  (let [build {:started 0 :completed 1100
               :timings [{:name "a" :real 0.1}
                         {:name "b" :real 0.2}]}]
    (is (= {"total" [1.1], "a" [0.1], "b" [0.2]}
           (summary/timing-map build)))))

(deftest average-timings
  (let [b1 {:started 0 :completed 500
            :timings [{:name "a" :real 0.1}
                      {:name "b" :real 0.2}]}
        b2 {:started 0 :completed 1000
            :timings [{:name "b" :real 0.2}]}
        build-times {"total" {:mean 0.75, :median 0.75, :percentile-90 0.95},
                     "a" {:mean 0.1, :median 0.1, :percentile-90 0.1},
                     "b" {:mean 0.2, :median 0.2, :percentile-90 0.2}}]
    (is (= build-times
           (summary/build-time [b1 b2])))
    ;; Absent value "a" in b2 is not contributing to the mean
    (is (= {"total" 0.75 "a" 0.1 "b" 0.2} (summary/mean build-times)))))
