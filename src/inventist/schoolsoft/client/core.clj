(ns inventist.schoolsoft.client.core
  (:require [clojure.java.io :as io]
            [ysera.test :refer [is-not]]
            [inventist.util.core :as util]))

(comment "Here people data is converted to Datomic transactions from tsv files that have been redacted from"
         "this git-repo for natural reasons.")

(def staff-group-temp-id "personal-group-temp-id")
(def department-group-temp-id "department-group-temp-id")

(defn schoolsoft-groups->db-people-groups [schoolsoft-groups]
  (->> schoolsoft-groups
       (map (fn [group] {:group/schoolsoft-id (:id group)
                         :group/name          (:name group)
                         :group/description   (:description group)
                         :group/school-class  (= "1" (:classtype group))
                         :group/active        (= "1" (:active group))}))
       (concat [{:db/id              staff-group-temp-id
                 :group/name         "Personal"
                 :group/description  "Staff of the school."
                 :group/school-class false
                 :group/active       true}])
       (concat [{:db/id              department-group-temp-id
                 :group/name         "Avdelningar"
                 :group/description  "Avdelningar på skolan (ej riktiga personer)."
                 :group/school-class false
                 :group/active       true}])))

(defn schoolsoft-students->db-persons [schoolsoft-students]
  (->> schoolsoft-students
       (map (fn [student] (merge {:person/schoolsoft-id (:id student)
                                  :person/first-name    (:fname student)
                                  :person/last-name     (:lname student)
                                  :person/gender        (:sex student)
                                  :person/occupation    :student
                                  :person/groups        [{:group/schoolsoft-id (:classid student)}]
                                  :person/active        (= "1" (:active student))
                                  :person/photo-url     (:picture student)
                                  :person/phone         (:mobile student)
                                  :person/address       (:address1 student)}
                                 (let [username (:username student)]
                                   (when (not (empty? username))
                                     {:person/username username}))
                                 (let [emails (remove nil? [(when (not-empty (:email student))
                                                              (:email student))
                                                            (when (not-empty (:username student))
                                                              (str (:username student) "@gripsholmsskolan.se"))])]
                                   (when (not (empty? emails))
                                     {:person/email emails})))))))

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
              (merge {:person/schoolsoft-id (str "s" (:id staff))
                      :person/groups        [staff-group-temp-id]
                      :person/first-name    (:fname staff)
                      :person/last-name     (:lname staff)
                      :person/active        (= "1" (:active staff))
                      :person/occupation    :staff
                      :person/phone         (:mobile staff)
                      :person/address       (:address1 staff)}
                     (let [username (:username staff)]
                       (when (not (empty? username))
                         {:person/username username}))
                     (let [emails (remove nil? [(when (not-empty (:email staff))
                                                  (:email staff))
                                                (when (not-empty (:username staff))
                                                  (str (:username staff) "@gripsholmsskolan.se"))])]
                       (when (not (empty? emails))
                         {:person/email emails})))))
       (concat [{:person/groups        [department-group-temp-id]
                 :person/first-name    "IT"
                 :person/last-name     "Department"
                 :person/active        true
                 :person/occupation    :staff
                 :person/phone         "070-791 48 11"}])))

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
