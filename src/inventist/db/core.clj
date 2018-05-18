(ns inventist.db.core
  (:require [datomic.api :as d]
            [inventist.db.schema :as schema]
            [ysera.test :refer [is=]]
            [clojure.string :as str]
            [inventist.schoolsoft.client.core :as schoolsoft]
            [inventist.datorbasen.client.core :as datorbasen]))

(def docker-local-uri
  "datomic:free://localhost:4334/inventist")
(def in-memory-uri
  "datomic:mem://inventist")
(def test-uri
  "datomic:mem://test")

(defn clear-database! [uri]
  (d/delete-database uri)
  (d/create-database uri))

(defn create-fresh-test-database! []
  (clear-database! test-uri)
  (let [conn (d/connect test-uri)]
    (d/transact conn schema/entire-schema)
    conn))


(defn import-fresh-database!
  [db-uri]
  (clear-database! db-uri)
  (let [db-connection (d/connect db-uri)]
    (d/transact db-connection (concat schema/entire-schema
                                      [[:db/add "datomic.tx" :db/txInstant #inst "2015-01-01"]]))

    (d/transact db-connection (concat (schoolsoft/create-all-data-transaction)
                                      [[:db/add "datomic.tx" :db/txInstant #inst "2015-01-01"]]))

    (doall
      (->> (datorbasen/create-transactions-for-all-registrations (d/db db-connection))
           (map (fn [transaction] (d/transact db-connection transaction)))))

    db-connection))

(defn keyword?->string
  {:test (fn []
           (is= (keyword?->string :test)
                "test")
           (is= (keyword?->string 42)
                42))}
  [k]
  (if (keyword? k)
    (-> k
        (str)
        (str/replace ":" ""))
    k))

(defn pulled-keyword-to-graphql-keyword
  [k]
  (-> k
      (str/replace ":" "")
      (str/split #"/")
      second
      (str/replace "-" "_")
      keyword))


(defn get-people
  {:test (fn [] (is=
                  (let [conn (create-fresh-test-database!)]
                    (d/transact conn
                                [{:person/schoolsoft-id "test"
                                  :person/first-name    "Lisa"}
                                 {:person/schoolsoft-id "test2"
                                  :person/first-name    "Per"}])
                    (->> (get-people (d/db conn))
                         (map (fn [person] (dissoc person :id)))
                         (into #{})))
                  #{{:schoolsoft_id "test"
                     :first_name    "Lisa"}
                    {:schoolsoft_id "test2"
                     :first_name    "Per"}}))}
  [db]
  (->> (d/q '[:find (pull ?e ["*"])
              :where
              [?e :person/schoolsoft-id _]]
            db)
       (map first)
       (map (fn [result]
              (->> result
                   (map (fn [[k v]]
                          [(pulled-keyword-to-graphql-keyword k)
                           (keyword?->string v)]))
                   (into {}))))))

(defn get-group
  [db {db-group-id :db-group-id}]
  (->> (d/pull db ["*"] db-group-id)
       (map (fn [[k v]]
              [(pulled-keyword-to-graphql-keyword k)
               (keyword?->string v)]))
       (into {})))

(comment
  (-> {:födelsedag                           "0000-00-00",
       :klass                                "7 Tigrar",
       :smart-status                         "SMART Status: Verified",
       :anmärkning                           "",
       :förnamn                              "Vendela",
       :fyra-sista-siffrorna-i-personnummret "XXXX",
       :ram                                  "8192",
       :serienummer                          "FVHTPSRXJ1WK",
       :mac-address                          "d4:61:9d:1b:09:d2",
       :ssd                                  "TRUE",
       :status                               "",
       :namn-på-hårddisk                     "Macintosh HD",
       :os-x-version                         "10.12.6",
       :efternamn                            "Wohrne",
       :modellnamn                           "MacBook Air (13-inch, 2017)",
       :ledig-hårddisk                       "83",
       :kan-lösenord                         "TRUE",
       :timestamp                            "2018-02-16T22:04:22",
       :diagnostik                           "",
       :modell                               "MacBookAir7,2",
       :hårddisk                             "120"}
      (datorbasen/datorbasen-registration->inventory-item (d/db db-connection)))

  (d/q '[:find ?e ?name ?lname ?computer                    ;(pull ?ce ["*"])
         :where
         [?e :person/first-name ?name]
         [?e :person/last-name ?lname]
         [?e :person/active true]
         [?e :person/groups ?group]
         [?group :group/name "7-Leoparder"]
         [?ce :inventory-item/users ?e]
         [?ce :inventory-item/model-name ?computer]
         [?ce :inventory-item/users ?users]]
       (d/db (import-fresh-database! in-memory-uri)))

  (get-people (d/db (import-fresh-database! in-memory-uri)))
  (get-group (d/db (d/connect in-memory-uri)) {:db-group-id 17592186045486}))


