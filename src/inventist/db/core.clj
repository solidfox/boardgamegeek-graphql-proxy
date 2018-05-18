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

(defn pulled-keyword->graphql-keyword
  [k]
  (-> k
      (str/replace ":" "")
      (str/split #"/")
      second
      (str/replace "-" "_")
      keyword))

(defn pulled-result->graphql-result
  [result]
  (->> result
       (map (fn [[k v]]
              [(pulled-keyword->graphql-keyword k)
               (keyword?->string v)]))
       (into {})))


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
  [db & [{groups :groups}]]
  (->> (d/q (if groups
              '[:find (pull ?e ["*"])
                :in $ [?group ...]
                :where
                [?e :person/schoolsoft-id]
                [?e :person/groups ?group-eid]
                (or [(= ?group ?group-eid)]
                    [?group-eid :group/name ?group])]
              '[:find (pull ?e ["*"])
                :where
                [?e :person/schoolsoft-id]])
            db
            groups)
       (map first)
       (map pulled-result->graphql-result)))

(defn query-inventory [db {search-terms :search_terms}]
  (->> (d/q (if search-terms
              '[:find (pull ?e ["*"])
                :in $ [?search-terms ...]
                :where
                (or [?e :inventory-item/serial-number ?v]
                    [?e :inventory-item/model-name ?v])
                [(str "(?i)" ?search-terms) ?pattern-str]
                [(re-pattern ?pattern-str) ?pattern]
                [(re-find ?pattern ?v)]]
              '[:find (pull ?e ["*"])
                :where
                (or [?e :inventory-item/serial-number]
                    [?e :inventory-item/name]
                    [?e :inventory-item/brand]
                    [?e :inventory-item/image-url])])
            db
            search-terms)
       (map first)
       (map pulled-result->graphql-result)))

(defn get-inventory-of-person
  [db {person-db-id :person-db-id}]
  (->> (d/q '[:find (pull ?e ["*"])
              :in $ ?person-eid
              :where [?e :inventory-item/users ?person-eid]]
            db
            person-db-id)
       (map first)
       (map pulled-result->graphql-result)
       (map (fn [result] (assoc result :class "laptop")))))


(defn get-group
  [db {group-eid :group-db-id}]
  (->> (d/pull db ["*"] group-eid)
       (map (fn [[k v]]
              [(pulled-keyword->graphql-keyword k)
               (keyword?->string v)]))
       (into {})))

(defn get-person [db {person-eid :person-db-id}]
  (->> (d/pull db ["*"] person-eid)
       (map (fn [[k v]]
              [(pulled-keyword->graphql-keyword k)
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

  (-> (get-people (d/db (import-fresh-database! in-memory-uri)) {:groups ["7-Tigrar"]})
      (last)
      ((fn [{id     :id
             groups :groups
             :as    person}]
         (def group-eid (get (first groups) ":db/id"))
         (def person-eid id)
         person)))
  (d/q '[:find (pull ?e ["*"])
         :in $ ?person-eid
         :where [?e :inventory-item/users ?person-eid]]
       (d/db (d/connect in-memory-uri))
       17592186045955)

  (query-inventory (d/db (d/connect in-memory-uri)) {:search_terms ["2012"]})
  (get-group (d/db (d/connect in-memory-uri)) {:group-db-id group-eid})
  (get-person (d/db (d/connect in-memory-uri)) {:person-db-id person-eid})
  (get-inventory-of-person (d/db (d/connect in-memory-uri)) {:person-db-id person-eid}))





