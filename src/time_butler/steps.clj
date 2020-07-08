(ns time-butler.steps
  "Classify timing data based on if the build step is run in parallel or one after
  the other.")

;; this represents our currently timed build process and is used to auto generate the charts
(def build-tree
  {:duration {:python_dependencies {}
              :asset_dependencies.sh {}
              :run-jetpack.sh {} ; TODO handle pre/post actions?
              :precompile {}
              :db_recreate {}
              :migrations {}
              ;; TODO might be nice to have this take the max of sub times for the rollup
              :parallel. {:parallel.filesize {}
                          :parallel.karma {}
                          :parallel.violations {}
                          :parallel.elm {:elm.run-elm-tests {:elm.run-18-add-dependencies {}
                                                             :elm.run-verify-examples {}
                                                             :elm.run-18-tests {}}
                                         :elm.generate-elm-doc {}}
                          :parallel.ruby-specs {:ruby-specs.parallel_group {}
                                                :ruby-specs.retry_flakes {}
                                                :ruby-specs.parallel-prepare {}}
                          :parallel.versions {}}}})

(defn- leaves [[k v]] (if (empty? v) k (map leaves v)))
(defn- composites [[k v]] (if (empty? v) [] (cons k (map composites v))))
(defn- all [[k v]] (cons k (map all v)))

(def parallel-steps
  (->> (get-in build-tree [:duration :parallel.])
       (map leaves)
       flatten
       (into #{})))

(def non-parallel-steps
  (->> (dissoc (:duration build-tree) :parallel.)
       (map leaves)
       flatten
       (into #{})))

(def known-steps
  (->> build-tree
       (map all)
       flatten
       (into #{})))

(def composite-steps
  (->> build-tree
       (map composites)
       flatten
       (into #{})))

(defn- parallel-step?
  "Helper to determine if the given step is under the parallel fold"
  [step]
  (contains? parallel-steps (keyword step)))

(defn- non-parallel-step?
  "Helper to determine if the given step is outside the parallel fold"
  [step]
  (contains? non-parallel-steps (keyword step)))

(defn- known-step?
  "Helper to determine if the given step is included in build-tree"
  [step]
  (contains? known-steps (keyword step)))

(defn- composite-step?
  "Helper to determine if the given step is a parent of subsequent build steps"
  [step]
  (contains? composite-steps (keyword step)))

(defn step-type? [step]
  (cond
    (parallel-step? step) :parallel
    (non-parallel-step? step) :non-parallel
    (composite-step? step) :composite
    :else :unknown))
