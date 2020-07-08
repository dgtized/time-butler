(ns time-butler.sequential-test
  (:require [time-butler.sequential :as sequential]
            [clojure.test :refer :all]))

(deftest collapse
  (let [in-seq? (sequential/in-seq identity inc)
        collapse (partial sequential/collapse in-seq? identity)]
    (is (= '() (collapse [])))
    (is (= '(1) (collapse [1])))
    (is (= '(1 3) (collapse [1 3])))
    (is (= '(1 2) (collapse [1 2])))
    (is (= '((1 2 3) 5) (collapse [1 2 3 5])))
    (is (= '(1 (3 4 5) (7 8 9) 11) (collapse [1 3 4 5 7 8 9 11])))
    (is (= '(1 (3 4 5)) (collapse [1 3 4 5])))))
