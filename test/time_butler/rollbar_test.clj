(ns time-butler.rollbar-test
  (:require [clojure.test :refer :all]
            [java-time :as t]
            [time-butler.config :as config]
            [time-butler.rollbar :as r]))

(deftest uuid-generation
  (let [spec {:name "a" :classname "b" :file "c" :build-id ["a" 1]}]
    (is (= #uuid "23162339-d891-5d59-809f-2f1efe29d1b7"
           (r/spec->uuid spec)))
    (is (= #uuid "98494517-9100-5dc3-9b6b-75fb76f5abf1"
           (r/spec->uuid (assoc spec :file "d"))))
    (is (= #uuid "eff4dfdb-bf6a-597c-b554-0975f39bc889"
           (r/spec->uuid (assoc spec :build-id ["a" 2]))))))

(deftest url-generation
  (let [vec-spec
        {:name "a" :classname "b" :file "c"
         :builds [{:build-id ["a" 1]}]}
        list-spec
        {:name "a" :classname "b" :file "c"
         :builds (list {:build-id ["a" 1]})}
        url
        (str "https://rollbar.com/item/uuid/?uuid="
             #uuid "23162339-d891-5d59-809f-2f1efe29d1b7")]
    (is (= url (r/item-url vec-spec)))
    (is (= url (r/item-url list-spec)))))

(deftest reporting
  (let [revision "888b140afc4dd33dddbc9d8dbe2ab2b55e37c245"
        failing-specs
        [{:name "after the parakeets attacked, all was lost"
          :classname "spec.features.parakeet.attack_spec"
          :file "./spec/features/parakeet/attacks_spec.rb"
          :build-id ["master" 4129]
          :build-url "https://jenkins/job/master/4129"
          :build-grade :failure
          :time 156.947576
          :started (t/java-date 1532069767000)
          :revision {:head revision :master revision}
          :node "ci10"}
         {:name "suddenly, giraffes!"
          :classname "spec.lib.suddenly.giraffes_spec"
          :file "./spec/lib/suddenly/giraffes_spec.rb"
          :build-id ["master" 4124]
          :build-url "https://jenkins/job/master/4124"
          :build-grade :flake
          :time 0.653179
          :started (t/java-date 1532049403000)
          :revision {:head revision :master revision}
          :node "ci11"}]
        reports (r/report-spec failing-specs)]
    (is (= 2 (count reports)))
    (is (= {:request {:url "https://jenkins/job/master/4129"
                      :build-grade :failure}
            :server {:host "ci10"}
            :level "warning"
            :code_version revision
            :notifier {:name "TimeButler" :version (config/version)}
            :uuid #uuid "1b1c3b68-bfba-5ec6-b95d-f991162a111b"
            :timestamp 1532069767000
            :body {:message {:classname "spec.features.parakeet.attack_spec"
                             :file "./spec/features/parakeet/attacks_spec.rb"
                             :body "after the parakeets attacked, all was lost"
                             :duration 156.947576
                             :timestamp "2018-07-20T06:56:07Z"}}}
           (-> reports second)))))

(deftest trim-failing
  (let [a {:build 1 :build-grade :success :revision 1}
        b {:build 2 :build-grade :flake :revision 1}
        c {:build 3 :build-grade :failure :revision 2}
        d {:build 4 :build-grade :failure :revision 3}]
    (is (= [a] (r/trim-last-failures [a]))
        "keep successes")
    (is (= [a] (r/trim-last-failures [a c]))
        "trim last failure")
    (is (= [a] (r/trim-last-failures [a c c]))
        "trim multiple failures")
    (is (= [a b] (r/trim-last-failures [a b c]))
        "keep flakes")
    (is (= [c a c b] (r/trim-last-failures [c a c b]))
        "keep starting/middle failures")
    (is (= [a b c] (r/trim-last-failures [a b c d]))
        "stop dropping if revision changes")))
