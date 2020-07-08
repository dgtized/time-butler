(ns time-butler.config-test
  (:require [time-butler.config :as sut]
            [clojure.test :as t]))

(t/deftest synthetics
  (t/is (= {"a" max
            "b" +}
           (sut/synthetic-fields {:synthetic-fields {"a" :parallel
                                                     "b" :sequential}})))
  (t/is (= {}
           (sut/synthetic-fields {:synthetic-fields {"a" :unknown}}))
        "ignore fields with unknown summarization function")
  (t/is (= {} (sut/synthetic-fields {}))
        "configuration value :synthetic-fields is optional"))

(t/deftest db
  (let [getenv (fn [x] (if (= x "BUTLER_BUCKET") "env.s3" "env.local"))]
    (t/is (= {:bucket "env.s3" :local-cache "env.local"}
             (sut/db {} getenv))
          "bucket specified by BUTLER_BUCKET")
    (t/is (= {:bucket "config.s3" :local-cache "config.local"}
             (sut/db {:storage {:bucket "config.s3" :local-cache "config.local"}})))
    (t/is (= {:bucket "env.s3" :local-cache "env.local"}
             (sut/db {:storage {:bucket "config.s3" :local-cache "config.local"}}
                     getenv)))
    (t/is (thrown? Exception (sut/db {}))
          "s3 bucket configuration is mandatory")
    (t/is (= {:bucket "config.s3" :local-cache "jenkins-builds"}
             (sut/db {:storage {:bucket "config.s3"}}))
          "local cache falls back to default jenkins-builds if nothing specified")))
