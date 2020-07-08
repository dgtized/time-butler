(ns time-butler.timings-test
  (:require [clojure.test :refer :all]
            [time-butler.timings :refer :all]))

(deftest synthetic-timing
  (let [timings [{:name "compile.a" :real 1.0}
                 {:name "compile.b" :real 2.0}
                 {:name "foo.a" :real 0.0}]]
    (is (= {:name "compile" :real 2.0}
           (synthetic-time max "compile" timings)))
    (is (= {:name "compile" :real 3.0}
           (synthetic-time + "compile" timings)))
    (is (= nil (synthetic-time + "bar" timings)))
    (is (= [{:name "compile" :real 2.0} {:name "foo" :real 0.0}]
           (synthesize timings {"compile" max "foo" + "bar" +})))))

(deftest test-impute
  (let [builds [{:timings [{:name "A" :real 1.0}]}
                {:timings [{:name "A" :real 1.0}
                           {:name "B" :real 1.0}]}]]
    (is (= (impute-timings builds)
           [{:timings [{:name "A" :real 1.0}
                       {:name "B" :real 0.0}]}
            {:timings [{:name "A" :real 1.0}
                       {:name "B" :real 1.0}]}]))))

(deftest test-safe-group
  (let [builds [{:one "alpha"
                 :timings [{:name "A" :real 30.0}]}
                {:timings [{:name "A" :real 32.0}
                           {:name "B" :real 45.0}]}]]
    (is (= {:revision-head [nil nil]
            :one ["alpha" nil]
            :two [nil nil]
            "A" [0.5 0.53]
            "B" [0.0 0.75]}
           (group-timings [:one :two]
                          (impute-timings builds))))))

(deftest level-test
  (is (= [] (level :timings :name [])))

  (is (= [{:build 1 :timings [{:name "foo" :real 0.1}
                              {:name "bar" :real 0.2}
                              {:name "baz" :real 0.0}]}
          {:build 2 :timings [{:name "bar" :real 0.3}
                              {:name "baz" :real 0.4}
                              {:name "foo" :real 0.0}]}]
         (level :timings :name
                [{:build 1 :timings [{:name "foo" :real 0.1}
                                     {:name "bar" :real 0.2}]}
                 {:build 2 :timings [{:name "bar" :real 0.3}
                                     {:name "baz" :real 0.4}]}]))))
