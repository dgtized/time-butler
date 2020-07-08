(ns time-butler.domain
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]))

;; for config
(s/def ::period #{:hour :day :week :fortnight :month :year})

(s/def ::a-build
  (s/and
   (s/keys :req-un [::branch ::build ::build-id
                    ::started ::completed ::node
                    ::revision
                    ;; ::environment
                    ::timings
                    ;; ::assets
                    ::build-url
                    ])
   (fn [build] (= (get-in build [:build-id 1]) (get build :build)))))

(s/def ::branch string?)
(s/def ::build pos-int?)
(s/def ::build-id (s/tuple ::branch ::build))
(s/def ::started inst?)
(s/def ::completed inst?)
(s/def ::node string?)
(s/def ::revision (s/keys :req-un [::head ::master]))
(s/def ::head string?)
(s/def ::master string?)

(s/def ::timings (s/coll-of ::step-time :gen-max 100))
(s/def ::step-time (s/keys :req-un [::name ::real]))
(s/def ::name string?)
(s/def ::real (s/double-in :min 0.0 :max (float (* 24 60 60))))

(s/def ::build-url string?)

(comment (gen/sample (s/gen ::a-build) 1)
         (gen/sample (s/gen ::revision) 1)
         (s/explain ::revision {:head "foo" :master "bar" :other "baz"})
         (s/conform ::a-build (first time-butler.build/builds))
         (s/explain ::a-build (first time-butler.build/builds)))
