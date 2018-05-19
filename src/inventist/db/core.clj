(ns inventist.db.core
  (:require [datomic.api :as d]
            [inventist.db.schema :as schema]
            [ysera.test :refer [is=]]
            [clojure.string :as str]
            [clj-time.format :as time]
            [clj-time.coerce :refer [from-date]]
            [inventist.schoolsoft.client.core :as schoolsoft]
            [inventist.datorbasen.client.core :as datorbasen]
            [com.walmartlabs.lacinia.schema :refer [tag-with-type]]))

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

(defn correct-person-photo-url [person]
  (if (not-empty (:photo_url person))
    (assoc person :photo_url (str (:schoolsoft_id person) ".jpg"))
    (dissoc person :photo_url)))


(defn get-person [db {person-eid :person-db-id}]
  (->> (d/pull db ["*"] person-eid)
       (map (fn [[k v]]
              [(pulled-keyword->graphql-keyword k)
               (keyword?->string v)]))
       (into {})
       (correct-person-photo-url)))

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
       (map pulled-result->graphql-result)
       (map correct-person-photo-url)))

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

(defn get-inventory-history-of-item
  [db {id :inventory-item-db-id}]
  (->> (d/q '[:find ?user ?instant
              :in $ ?inventory-item-eid
              :where
              [?inventory-item-eid :inventory-item/users ?user ?tx]
              [?tx :db/txInstant ?instant]]
            (d/history db)
            id)
       (map (fn [[new-user instant]]
              (tag-with-type {:new_user new-user
                              :instant  (time/unparse (time/formatters :date-time-no-ms) (from-date instant))}
                             :Reallocation)))))

(defn get-group
  [db {group-eid :group-db-id}]
  (->> (d/pull db ["*"] group-eid)
       (map (fn [[k v]]
              [(pulled-keyword->graphql-keyword k)
               (keyword?->string v)]))
       (into {})))


(comment
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
  (d/q '[:find ?a ?v ?tx ?t
         :in $ ?item-eid
         :where
         [?item-eid _ _ ?tx]
         [?item-eid ?a ?v ?tx]
         [?tx :db/txInstant ?t]]
       (d/history (d/db (d/connect in-memory-uri)))
       17592186046470)

  (query-inventory (d/db (d/connect in-memory-uri)) {:search_terms ["2012"]})
  (get-group (d/db (d/connect in-memory-uri)) {:group-db-id group-eid})
  (get-person (d/db (d/connect in-memory-uri)) {:person-db-id person-eid})
  (get-inventory-of-person (d/db (d/connect in-memory-uri)) {:person-db-id person-eid})
  (get-inventory-history-of-item (d/db (d/connect in-memory-uri)) {:inventory-item-db-id 17592186046563}))






