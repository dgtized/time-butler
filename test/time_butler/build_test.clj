(ns time-butler.build-test
  (:require [clojure.test :refer :all]
            [me.raynes.fs :as fs]
            [time-butler.build :refer :all]))

(deftest test-successful-revisions
  (let [builds [{:revision {:head "AA" :master "MA"} :build-id ["m" 10] :success true}
                {:revision {:head "BB" :master "MA"} :build-id ["m" 11] :success false}
                {:revision {:head "CC" :master "MB"} :build-id ["m" 12] :success false}
                {:revision {:head "CC" :master "MB"} :build-id ["m" 13] :success true}
                {:revision {:head "CC" :master "MB"} :build-id ["m" 14] :success true}
                {:revision {:head "CC" :master "MC"} :build-id ["m" 15] :success true}]]
    (is (= (successful-revisions builds)
           {{:head "AA" :master "MA"} #{["m" 10]}
            {:head "CC" :master "MB"} #{["m" 13] ["m" 14]}
            {:head "CC" :master "MC"} #{["m" 15]}}))
    (is (= ((successful-revisions builds) {:head "CC" :master "MB"})
           #{["m" 13] ["m" 14]}))
                                        ; test when there are no successful builds (bug charting a branch)
    (is (= ((successful-revisions (remove :success builds)) {:head "BB" :master "MA"})
           nil))
    (is (= (map #(select-keys % [:build-id :flakes]) (annotate-flakes builds))
           [{:build-id ["m" 10] :flakes {:success-in #{["m" 10]} :flaky false}}
            {:build-id ["m" 11] :flakes {:success-in nil :flaky false}}
            {:build-id ["m" 12] :flakes {:success-in #{["m" 13] ["m" 14]} :flaky true}}
            {:build-id ["m" 13] :flakes {:success-in #{["m" 13] ["m" 14]} :flaky false}}
            {:build-id ["m" 14] :flakes {:success-in #{["m" 13] ["m" 14]} :flaky false}}
            {:build-id ["m" 15] :flakes {:success-in #{["m" 15]} :flaky false}}]))))

(def environment-build (fs/file "resources/test/master-build-1-1501165800"))

(deftest failing-build-manifest-directory
  (is (= {:action "build"
          :started #inst "2017-07-27T14:30:00.000-00:00"
          :completed #inst "2017-07-27T14:46:40.000-00:00"
          :duration 16.66
          :timings [{:name "bootstrap.a" :real 15.23}
                    {:name "bootstrap.b" :real 11.18}
                    {:name "bootstrap" :real 15.23}]
          :assets
          {:jetpack {:preexisting "true" :run "first_try"}
           :precompile {:cache "miss" :key "pre-v1-aba"}}
          :environment
          {:cache_tarball "v2.tgz"}
          :branch "master-build"
          :build-id ["master-build" 1]
          :build-url "https://jenkins.url/job/project/job/master-build/1/"
          :revision {:head "abaca" :master "dabraca"}
          :build 1
          :directory environment-build
          :node "n02"
          :success false
          :failures
          #{"test.ruby-specs"
            "rspec failed, but retry_flakes.rb failed to parse flakes."}
          :specs
          [{:classname "package.class.one", :file "./spec/package/class/one.rb", :name "Test2", :time 3.0, :tests 2}
           {:classname "package.class.two", :file "./spec/package/class/two.rb", :name "Test", :time 2.0, :tests 1}
           {:classname "package.class.two", :file "./spec/package/class/two.rb", :name "Failing Test", :time 0.5, :tests 1}
           {:classname "package.class.one", :file "./spec/package/class/one.rb", :name "Test1", :time 0.5, :tests 1}]
          :failing-specs
          [{:classname "package.class.two" :file "./spec/package/class/two.rb"
            :name "Failing Test" :time 0.5 :annotation [:failure]}]
          :catastrophic false
          :flakes {:success-in nil :flaky false}
          :build-grade :failure}
         (first (process-builds {:synthetic-fields {"bootstrap" :parallel}}
                                [environment-build])))))

(defn failures
  [examples]
  (for [[name time] examples]
    {:name (format "%s.1" name), :time time
     :classname name :file (str name ".rb")}))

(deftest test-flaking-specs
  (is (= [{:classname "a" :file "a.rb" :name "a.1"
           :builds [{:build-id ["master" 1] :build-url "master/1" :time 3.0 :started 1}
                    {:build-id ["master" 2] :build-url "master/2" :time 4.0 :started 2}
                    {:build-id ["bob" 1] :build-url "bob/1" :time 4.0 :started 4}]}
          {:classname "b" :file "b.rb" :name "b.1"
           :builds [{:build-id ["master" 1] :build-url "master/1" :time 2.0 :started 1}]}]
         (flaking-specs
          [{:build-id ["master" 1] :build-url "master/1"
            :build-grade :flake :started 1
            :failing-specs (failures {"a" 3.0 "b" 2.0})}
           {:build-id ["master" 2] :build-url "master/2"
            :build-grade :flake :started 2
            :failing-specs (failures {"a" 4.0})}
           ;; excluded because not flakey
           {:build-id ["master" 3] :build-url "master/3"
            :build-grade :failing :started 3
            :failing-specs (failures {"a" 4.0})}
           ;; included on another branch
           {:build-id ["bob" 1] :build-url "bob/1"
            :build-grade :flake :started 4
            :failing-specs (failures {"a" 4.0})}]))))

(deftest test-statistics
  (let [success {:build-grade :success}
        failure {:build-grade :failure}
        flaky {:build-grade :flake}]
    (is (= {:total 3 :success 1 :failure 1 :flake 1}
           (statistics [success failure flaky])))))

(deftest test-failing-specs-with-build
  (is (= [{:name "a.1" :time 3.0 :classname "a" :file "a.rb"
           :build-id ["alpha" 1] :build-url "alpha/1"
           :started 1 :build-grade :flake
           :revision {:head "0" :master "1"} :node "1"}
          {:name "b.1" :time 2.0 :classname "b" :file "b.rb"
           :build-id ["alpha" 1] :build-url "alpha/1"
           :started 1 :build-grade :flake
           :revision {:head "0" :master "1"} :node "1"}
          {:name "a.1" :time 4.0 :classname "a" :file "a.rb"
           :build-id ["alpha" 3] :build-url "alpha/3"
           :started 3 :build-grade :failure
           :revision {:head "1" :master "1"} :node "2"}
          {:name "b.1" :time 3.0 :classname "b" :file "b.rb"
           :build-id ["alpha" 3] :build-url "alpha/3"
           :started 3 :build-grade :failure
           :revision {:head "1" :master "1"} :node "2"}
          {:name "a.1" :time 4.0 :classname "a" :file "a.rb"
           :build-id ["omega" 1] :build-url "omega/1"
           :started 4 :build-grade :flake
           :revision {:head "1" :master "2"} :node "3"}]
         (failing-specs-with-build
          [{:build-id ["alpha" 1] :build-url "alpha/1"
            :build-grade :flake :started 1
            :revision {:head "0" :master "1"} :node "1"
            :failing-specs (failures {"a" 3.0 "b" 2.0})}
           {:build-id ["alpha" 3] :build-url "alpha/3"
            :build-grade :failure :started 3
            :revision {:head "1" :master "1"} :node "2"
            :failing-specs (failures {"a" 4.0 "b" 3.0})}
           {:build-id ["omega" 1] :build-url "omega/1"
            :build-grade :flake :started 4
            :revision {:head "1" :master "2"} :node "3"
            :failing-specs (failures {"a" 4.0})}]))))
