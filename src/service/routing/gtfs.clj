(ns service.routing.gtfs
  "utilities for parsing GTFS files. See https://developers.google.com/transit/gtfs/reference/
  for more information"
  (:require [cheshire.core :as json]
            [clojure.data.csv :as csv]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [service.routing.utils.math :as math]))


;(def cities (atom nil))
;(reset! cities (get-supported-cities "resources/n7a235.json"))
;(do @cities)

;(defn- get-supported-cities
;  [filename]
;  (json/parse-string (slurp filename) keyword))

;; filters to decide how to parse a GTFS entry. If it is included here
;; we dont use the edn/read-string function but a custom one
(def omit-entries
  {:stops {:stop_name identity}
   :routes {:route_long_name identity
            :agency_id identity}
   :stop_times {:arrival_time identity ;;TODO: would it be better to use Date?
                :departure_time identity}
   :trips {:trip_headsign identity}})

(defn- csv->clj
  "parse a CSV file and return a sequence of maps with the values of
   each row. Assumes the first line contains the columns names. Replaces
   _ by -"
  [filename]
  (let [raw  (slurp filename)
        rows (csv/read-csv raw)
        fields (map keyword (first rows))]
    (sequence (map zipmap)
              (repeat fields) (rest rows))))

(defn parse-gtfs
  "takes a GTFS filename and coerces its content according to edn/read-string
  or omit-entries if any exist. Returns a sequence of map object with the content
  of the file"
  [filename]
  (let [[_ no-extension] (re-matches #"(.+)\.txt$" filename)
        type (keyword (last (str/split no-extension #"/")))
        omissions (type omit-entries)]
    (if (nil? omissions)
      (throw (ex-info (str "missing omissions entry " type) omit-entries))
      (for [m (csv->clj filename)]
        (into {} (for [[k v] m]
                   (if (contains? omissions k)
                     [k ((k omissions) v)]
                     [k (edn/read-string v)])))))))

#_(let [cities     (json/parse-string (slurp "resources/n7a235.json") keyword)]
      stops      (parse-gtfs "resources/gtfs/de/stops.txt")
      trips      (parse-gtfs "resources/gtfs/de/trips.txt")
      stop-times (parse-gtfs "resources/gtfs/de/stop_times.txt")
      routes     (parse-gtfs "resources/gtfs/de/routes.txt")
      stops2     (filter #(math/bbox-contains? (:bbox (:frankfurt cities))
                                               (:stop_lon %)
                                               (:stop_lat %))
                          stops)
      stop-ids    (into #{} (map :stop_id) stops2)
      stop-times2 (filter #(contains? stop-ids (:stop_id %)) stop-times)
      trip-ids    (into #{} (map :trip_id) stop-times2)
      trips2      (filter #(contains? trip-ids (:trip_id %)) trips)
      route-ids   (into #{} (map :route_id) trips2)
      routes2     (filter #(contains? route-ids (:route_id %)) routes)
    routes2)

;;; ---- example usages -----
;(csv->clj "resources/gtfs/de/routes.txt")
;(csv->clj "resources/gtfs/de/stops.txt")
;(first (parse-values (csv->clj "resources/gtfs/de/routes.txt")
;                     (:routes omit-entries))])))))
;(first (parse-values (csv->clj "resources/gtfs/de/stops.txt")
;                     (:stops omit-entries)))
;(take 5 (parse-values (csv->clj "resources/gtfs/de/stop_times.txt")
;                      (:stop-times omit-entries)))



