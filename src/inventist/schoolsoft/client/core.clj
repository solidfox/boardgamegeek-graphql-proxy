(ns inventist.schoolsoft.client.core)

(ns inventist.cache.core
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [datomic.api :as d]
            [inventist.db.core :as db]
            [ysera.test :refer [is-not]]))

(defn csv-data->maps [csv-data]
  (map zipmap
       (->> (first csv-data)                                ;; First row is the header
            (map keyword)                                   ;; Drop if you want string keys instead
            repeat)
       (rest csv-data)))

(defn read-tsv [path]
  (with-open [reader (io/reader path)]
    (doall
      (csv-data->maps (csv/read-csv reader
                                    :separator \tab)))))

(defn schoolsoft-groups->db-people-groups [schoolsoft-groups]
  (->> schoolsoft-groups
       (map (fn [group] {:group/schoolsoft-id (:id group)
                         :group/name          (:name group)
                         :group/description   (:description group)
                         :group/school-class  (= 1 (:classtype group))
                         :group/active        (= 1 (:active group))}))))

(defn schoolsoft-students->db-persons [schoolsoft-students]
  (->> schoolsoft-students
       (map (fn [student] {:person/schoolsoft-id (:id student)
                           :person/first-name    (:fname student)
                           :person/last-name     (:lname student)
                           :person/occupation    :student
                           :person/groups        [{:group/schoolsoft-id (:classid student)}]
                           :person/photo-url     (:picture student)
                           :person/email         (remove nil? [(when (not-empty (:email student))
                                                                 (:email student))
                                                               (str (:username student) "@gripsholmsskolan.se")])
                           :person/username      (:username student)
                           :person/phone         (:mobile student)
                           :person/address       (:address1 student)}))))

(defn schoolsoft-staff->db-persons
  {:test (fn []
           (is-not
             (-> [{:username ""}]
                 (schoolsoft-staff->db-persons)
                 (first)
                 (contains? :person/email))))}
  [schoolsoft-staff]
  (->> schoolsoft-staff
       (map (fn [staff]
              (let [email     (remove nil? [(when (not-empty (:email staff))
                                              (:email staff))
                                            (when (not-empty (:username staff))
                                              (str (:username staff) "@gripsholmsskolan.se"))])
                    email-map (when (not-empty email)
                                {:person/email email})]
                (merge {:person/schoolsoft-id (:id staff)
                        :person/first-name    (:fname staff)
                        :person/last-name     (:lname staff)
                        :person/occupation    :staff
                        :person/phone         (:mobile staff)
                        :person/address       (:address1 staff)}
                       email-map
                       (when (not-empty (:username staff))
                         {:person/username (:username staff)})))))))

(comment
  (do
    (->> (read-tsv (-> "confidential/people/groups.tsv"
                       io/resource
                       io/file))
         schoolsoft-groups->db-people-groups
         (d/transact db/conn))
    (->> (read-tsv (-> "confidential/people/students.tsv"
                       io/resource
                       io/file))
         schoolsoft-students->db-persons
         (d/transact db/conn))
    (->> (read-tsv (-> "confidential/people/staff.tsv"
                       io/resource
                       io/file))
         schoolsoft-staff->db-persons
         (d/transact db/conn))))


