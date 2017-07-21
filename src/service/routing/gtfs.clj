(ns service.routing.gtfs
  (:require [cheshire.core :as json]
            [clojure.data.csv :as csv]
            [clojure.string :as str]
            [clojure.edn :as edn]))


;(def cities (json/parse-string (slurp "resources/n7a235.json") keyword))

;; filters to decide how to parse a GTFS entry. If it is included here
;; we dont use the edn/read-string function but a custom one
(def omit-entries
  {:stops {:stop-name identity}
   :routes {:route-long-name identity
            :agency-id identity}})

(defn csv->clj
  "parse a CSV file and return a sequence of maps with the values of
   each row. Assumes the first line contains the columns names. Replaces
   _ by -"
  [filename]
  (let [raw  (slurp filename)
        rows (csv/read-csv raw)
        fields (map (comp keyword #(str/replace % "_" "-"))
                    (first rows))]
    (sequence (map zipmap)
              (repeat fields) (rest rows))))

(defn parse-values
  "takes a sequence of maps and a map of special keyword
  handlings and returns the sequence with values transformed"
  [maps omissions]
  (for [m maps]
    (into {}
      (for [[k v] m]
        (if (contains? omissions k)
          [k ((k omissions) v)]
          [k (edn/read-string v)])))))

;(first (parse-values (csv->clj "resources/gtfs/de/routes.txt")
;                     (:routes omit-entries))])))))
;(first (parse-values (csv->clj "resources/gtfs/de/stops.txt")
;                     (:stops omit-entries)))

;; example usages
;; (csv->clj "resources/gtfs/de/routes.txt")
;; (csv->clj "resources/gtfs/de/stops.txt")


