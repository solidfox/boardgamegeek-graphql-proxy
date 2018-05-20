(ns inventist.schoolsoft.client.core
  (:require [clojure.java.io :as io]
            [ysera.test :refer [is-not]]
            [inventist.util.core :as util]))

(comment "Here people data is converted to Datomic transactions from tsv files that have been redacted from"
         "this git-repo for natural reasons.")

(defn schoolsoft-groups->db-people-groups [schoolsoft-groups]
  (->> schoolsoft-groups
       (map (fn [group] {:group/schoolsoft-id (:id group)
                         :group/name          (:name group)
                         :group/description   (:description group)
                         :group/school-class  (= "1" (:classtype group))
                         :group/active        (= "1" (:active group))}))))

(defn schoolsoft-students->db-persons [schoolsoft-students]
  (->> schoolsoft-students
       (map (fn [student] {:person/schoolsoft-id (:id student)
                           :person/first-name    (:fname student)
                           :person/last-name     (:lname student)
                           :person/occupation    :student
                           :person/groups        [{:group/schoolsoft-id (:classid student)}]
                           :person/active        (= "1" (:active student))
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
                (merge {:person/schoolsoft-id (str "s" (:id staff))
                        :person/first-name    (:fname staff)
                        :person/last-name     (:lname staff)
                        :person/active        (= "1" (:active staff))
                        :person/occupation    :staff
                        :person/phone         (:mobile staff)
                        :person/address       (:address1 staff)}
                       email-map
                       (when (not-empty (:username staff))
                         {:person/username (:username staff)})))))))

(defn create-all-data-transaction []
  (concat
    (->> (util/read-tsv (-> "confidential/people/groups.tsv"
                            io/resource
                            io/file))
         schoolsoft-groups->db-people-groups)
    (->> (util/read-tsv (-> "confidential/people/students.tsv"
                            io/resource
                            io/file))
         schoolsoft-students->db-persons)
    (->> (util/read-tsv (-> "confidential/people/staff.tsv"
                            io/resource
                            io/file))
         schoolsoft-staff->db-persons)))
