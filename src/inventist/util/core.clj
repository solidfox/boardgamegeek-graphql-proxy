(ns inventist.util.core
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]))

(defn csv-data->maps [csv-data & [heading-transform]]
  (map zipmap
       (->> (first csv-data)                                ;; First row is the header
            (map (or heading-transform identity))
            (map keyword)                                   ;; Drop if you want string keys instead
            repeat)
       (rest csv-data)))

(defn read-tsv [path]
  (with-open [reader (io/reader path)]
    (doall
      (csv-data->maps (csv/read-csv reader
                                    :separator \tab)))))

(defn read-csv [path & [heading-transform]]
  (with-open [reader (io/reader path)]
    (doall
      (csv-data->maps (csv/read-csv reader
                                    :separator \,) heading-transform))))

(defn spy [x]
  (pprint x)
  x)
