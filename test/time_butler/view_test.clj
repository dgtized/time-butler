(ns time-butler.view-test
  (:require [clojure.test :refer :all]
            [time-butler.view :as v]))

(deftest test-jenkins-build-link
  (is (= [:a {:href "https://jenkins/bar-baz/11" :title "bar-baz#11"} 11]
         (v/jenkins-build-link {:build-id ["bar-baz" 11]
                                :build-url "https://jenkins/bar-baz/11"})))
  (is (= [:a {:href "https://jenkins/blah-blah/9"
              :title "blah-blah#9 at 2018-03-02T14:13:20Z"} 9]
         (v/jenkins-build-link {:build-id ["blah-blah" 9]
                                :build-url "https://jenkins/blah-blah/9"
                                :started #inst "2018-03-02T14:13:20Z"})))
  (is (= [:a {:href "ftp://blah-blah/9"
              :title "blah-blah#9 at 2018-03-02T14:13:20Z\n5 failing specs"} 9]
         (v/jenkins-build-link {:build-id ["blah-blah" 9]
                                :build-url "ftp://blah-blah/9"
                                :failing-specs (range 5)
                                :started #inst "2018-03-02T14:13:20Z"})))
  (is (= [:a {:href "https://jenkins/blah-blah/9"
              :title "blah-blah#9 at 2018-03-02T14:13:20Z in 0.20s"} 9]
         (v/jenkins-build-link {:build-id ["blah-blah" 9]
                                :build-url "https://jenkins/blah-blah/9"
                                :started #inst "2018-03-02T14:13:20Z"
                                :time 0.2}))))
