(ns time-butler.manifest-test
  (:require [clojure.test :refer :all]
            [me.raynes.fs :as fs]
            [time-butler.manifest :as manifest]))

(deftest sortable-nodes
  (is (= "ci02" (manifest/sortable-node "ci2")))
  (is (= "ci13" (manifest/sortable-node "ci13")))
  (is (= "n12" (manifest/sortable-node "n12")))
  (is (= "n05" (manifest/sortable-node "n5"))))

(def unknown-build (fs/file "resources/test/unknown-build-1-1501165800"))
(def minimal-build (fs/file "resources/test/minimal-build-1-1501165800"))
(def manifest-build (fs/file "resources/test/manifest-2-1533229809"))

(deftest test-parse-unknown-directory
  (is (nil? (manifest/parse {} unknown-build))))

(deftest test-parse-minimal-build-directory
  (is (= {:action "build"
          :started #inst "2017-07-27T14:23:53.000-00:00"
          :completed #inst "2017-07-27T14:30:00.000-00:00"
          :duration 6.11
          :assets {}
          :timings []
          :environment {:cache_tarball "v2.tgz"}
          :branch "minimal-build"
          :revision {:head "aba" :master "daba"}
          :build 1
          :build-id ["minimal-build" 1]
          :build-url "https://jenkins.url/job/project/job/minimal-build/1/"
          :directory minimal-build
          :node "n01"
          :success true
          :failures #{}}
         (manifest/parse {} minimal-build))))

(deftest parse-manifest-build-directory
  (is (= {:action "build"
          :branch "manifest"
          :build 2
          :build-id ["manifest" 2]
          :build-url "https://jenkins.url/job/project/job/manifest/2/"
          :started #inst "2018-08-02T16:09:27.000-00:00"
          :completed #inst "2018-08-02T17:10:09.000-00:00"
          :duration 60.7
          :directory manifest-build
          :node "n01"
          :revision {:head "ec33444d" :master "746f6950"}
          :environment
          {:cache_tarball "v2.tgz"
           :parallelism 6
           :percy_enable 0
           :sysconfcpus_processors 2}
          :assets
          {:jetpack {:preexisting "false" :run "first_try"}
           :precompile {:cache "hit" :key "precompile-v2-1c050"}}
          :timings
          [{:name "bootstrap.a" :real 15.23}
           {:name "bootstrap.b" :real 11.18}
           {:name "compile.assets" :real 1473.29}
           {:name "compile.database" :real 125.96}
           {:name "compile.rubocop" :real 140.74}
           {:name "test.elm" :real 2128.12}
           {:name "test.ruby-specs" :real 1376.82}
           ;; synthetics from post-process
           {:name "bootstrap" :real 15.23}
           {:name "compile" :real 1473.29}
           {:name "test" :real 2128.12}]
          :failures #{}
          :success true}
         (manifest/parse {"bootstrap" max "compile" max "test" max}
                         manifest-build))))
