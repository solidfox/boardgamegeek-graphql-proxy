(ns inventist.cache.core
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [datomic.api :as d]
            [inventist.db.core :as db]))

(defn csv-data->maps [csv-data]
  (map zipmap
       (->> (first csv-data)                                ;; First row is the header
            (map keyword)                                   ;; Drop if you want string keys instead
            repeat)
       (rest csv-data)))

(defn read-groups []
  (with-open [reader (io/reader "resources/confidential/people/groups.tsv")]
    (doall
      (csv-data->maps (csv/read-csv reader
                                    :separator \tab)))))

(comment "Groups look like this"
         {:maxstudent    "0",
          :description   "Åk 9",
          :classtype     "1",
          :leavepickup   "0",
          :mentortype    "1",
          :name          "Åk 9",
          :resourcetype  "0",
          :reviewtype    "1",
          :active        "1",
          :id            "271",
          :classplanning "1",
          :externalid    "{F95BADFC-E432-4268-8264-49BF02965DE2}"}
         {:maxstudent    "0",
          :description   "Åk 9:a",
          :classtype     "0",
          :leavepickup   "0",
          :mentortype    "0",
          :name          "Åk 9:a",
          :resourcetype  "0",
          :reviewtype    "1",
          :active        "1",
          :id            "176",
          :classplanning "0",
          :externalid    "{5F3E0A72-CF01-4777-9576-EA690B41B6E5}"})

(defn schoolsoft-groups->db-people-groups [schoolsoft-groups]
  (->> schoolsoft-groups
       (map (fn [group] {:group/schoolsoft-id (:id group)
                         :group/name          (:name group)
                         :group/description   (:description group)
                         :group/school-class  (= 1 (:classtype group))
                         :group/active        (= 1 (:active group))}))))

(comment
  #:group{:schoolsoft-id "278", :name "7-Leoparder:a", :description "7-Leoparder:a", :school-class false, :active false}
  #:group{:schoolsoft-id "322",
          :name "7-Leoparder:a musik",
          :description "7-Leoparder:a",
          :school-class false,
          :active false}
  (->> (read-groups)
       schoolsoft-groups->db-people-groups
       (d/transact db/conn)))


