(ns time-butler.s3
  "Helpers for interacting with AWS/S3 api"
  (:require [amazonica.aws.s3 :as s3]
            [clojure.java.io :refer [file]]
            [me.raynes.fs :as fs])
  (:import [com.amazonaws.services.s3.transfer KeyFilter TransferManagerBuilder]
           [com.amazonaws.util BinaryUtils Md5Utils]))

(defn aws-list
  "List keys in s3 bucket."
  [request]
  (let [response (s3/list-objects-v2 request)
        next-request (assoc request :continuation-token
                            (:next-continuation-token response))]
    (concat (:common-prefixes response)
            (if (:truncated? response)
              (lazy-seq (aws-list next-request))
              []))))

(defn file-etag
  "Calculates etag of downloaded file relative to directory or nil if missing."
  [directory filename]
  (let [f (file directory filename)]
    (when (fs/exists? f)
      (BinaryUtils/toHex (Md5Utils/computeMD5Hash f)))))

;; We may want to share TransferManager and enqueue everything to a single
;; manager in the future, but for now it is conceptually cleaner to parallelize
;; around the transfer manager.
(defn sync-directory
  "Sync all missing & locally modified files from remote S3 bucket."
  [bucket source-dir target-dir]
  (let [transfer (TransferManagerBuilder/defaultTransferManager)
        include-missing-or-changed
        (reify KeyFilter
          (shouldInclude [this summary]
            (let [key (.getKey summary)
                  etag (.getETag summary)]
              ;; exclude files that have already been downloaded
              (not= etag (file-etag target-dir key)))))]
    (.waitForCompletion
     (.downloadDirectory transfer
                         bucket
                         source-dir
                         target-dir
                         include-missing-or-changed))
    (.shutdownNow transfer)))

(comment (time (file-etag (file ".") "Jenkinsfile")))
