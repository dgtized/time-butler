(ns time-butler.rspec-test
  (:require [time-butler.rspec :as sut]
            [clojure.test :as t]
            [me.raynes.fs :as fs]))

(t/deftest load-spec-xml
  (t/is (= [{:tag :testsuite
             :attrs
             {:name "rspec" :hostname "somewhere" :timestamp "2019-06-13T05:20:00+00:00"
              :errors "0" :failures "1" :skipped "0" :tests "2" :time "1"}
             :content
             [{:tag :properties :attrs nil
               :content
               [{:tag :property :attrs {:value "1" :name "seed"} :content nil}]}
              {:tag :testcase
               :attrs
               {:name "Test1" :time "0.5"
                :file "./spec/package/class/one.rb"
                :classname "package.class.one"}
               :content nil}
              {:tag :testcase
               :attrs
               {:name "Failing Test" :time "0.5"
                :file "./spec/package/class/two.rb"
                :classname "package.class.two"}
               :content
               [{:tag :failure
                 :attrs
                 {:type "Capybara::ElementNotFound" :message "Unexpected results"}
                 :content ["Failure/Error: Unexected result"]}]}]}]
           (sut/load-spec-xml (fs/file "resources/test/junit")))))
