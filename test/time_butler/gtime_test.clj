(ns time-butler.gtime-test
  (:require [clojure.test :refer :all]
            [time-butler.gtime :refer :all]))

(deftest test-timestamp
  (is (= nil (timestamp nil)))
  (is (= #inst "2011-03-13T07:06:40.000-00:00" (timestamp 1300000000)))
  (is (= #inst "2017-07-14T16:33:20.000-00:00" (timestamp "1500050000"))))

(deftest t-duration
  (let [build {:started 1501165433500
               :completed 1501165800000}]
    (is (= 366.5 (duration build)))
    (is (= 6.1 (to-minutes-float (duration build))))))
