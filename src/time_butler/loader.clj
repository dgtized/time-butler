(ns time-butler.loader
  "Load standardized data from files."
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [me.raynes.fs :as fs]))

(defn json-slurp
  "Slurp a json `file` relative to `dir` or return {} if missing.

  Automatically converts json keys to lower case keywords."
  [dir file not-found]
  (let [path (fs/file dir file)]
    (if (fs/exists? path)
      (json/read-str (slurp path)
                     :key-fn (comp keyword str/lower-case))
      not-found)))

(defn slurp-contents
  "Slurp a file if it exists or return nil."
  [dir file]
  (let [path (fs/file dir file)]
    (when (fs/exists? path)
      (str/trim (slurp path)))))

(defn slurp-line-set [dir file]
  (->> (or (slurp-contents dir file) "")
       str/split-lines
       (remove empty?)
       set))
