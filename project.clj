(defproject time-butler "0.15.3"
  :description "Time performance of jenkins branches"
  :url "https://github.com/dgtized/time-butler"
  :license {:name "MIT Public License"
            :url  "https://opensource.org/licenses/MIT"}
  :plugins [[lein-tools-deps "0.4.5"]
            [lein-hiera "1.0.0"]
            [lein-cljfmt "0.6.3"]]
  :middleware [lein-tools-deps.plugin/resolve-dependencies-with-deps-edn]
  :lein-tools-deps/config {:config-files [:install :user :project]}
  :jvm-opts ["-Xmx3g" "-server"]
  :main ^:skip-aot time-butler.cli
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}
             :dev     {:dependencies [[org.clojure/test.check "0.9.0"]]}
             :ci      {:jvm-opts ["-Djava.awt.headless=true"]}}
  :hiera
  {:path "target/ns-hierarchy.png"
   :vertical false
   :cluster-depth 2
   :trim-ns-prefix true})
