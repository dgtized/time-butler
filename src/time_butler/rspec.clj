(ns time-butler.rspec
  "rspec test/unit xml parsing and processing"
  (:require [clojure.edn :as edn]
            [clojure.xml :as xml]
            [me.raynes.fs :as fs]
            [clojure.tools.logging :as log]))

(defn safely-parse [file]
  (try (xml/parse file)
       (catch org.xml.sax.SAXParseException e
         (log/errorf "Error parsing: %s" e)
         nil)))

(defn load-spec-xml [dirname]
  (->> (fs/glob dirname "*.xml")
       (filter #(pos? (fs/size %)))
       (keep safely-parse)))

(defn parse-spec [spec-xml]
  (->> spec-xml
       :content
       (map :attrs)
       (remove empty?)
       (group-by #(select-keys % [:classname :file :name]))
       (map (fn [[k v]]
              (let [times (map (comp edn/read-string :time) v)]
                (assoc k
                       :time (reduce + times)
                       :tests (count times)))))))

(defn all-specs [all-spec-xml]
  (->> all-spec-xml
       (mapcat parse-spec)
       (sort-by :time)
       (reverse)))

(defn extract-spec-failures [spec-xml]
  (->> spec-xml
       :content
       (filter (fn [x] (= (get-in x [:tag]) :testcase)))
       (map (fn [x] (let [annotation (get-in x [:content] [])]
                     (-> (:attrs x)
                         (update-in [:time] edn/read-string)
                         (assoc :annotation (map :tag annotation))))))
       (remove empty?)
       (remove (fn [{:keys [annotation]}]
                 (or (empty? annotation)
                     (every? #(= % :skipped) annotation))))))

(defn all-spec-failures [all-spec-xml]
  (mapcat extract-spec-failures all-spec-xml))
