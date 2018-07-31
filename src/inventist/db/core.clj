(ns inventist.db.core
  (:require [datomic.api :as d]
            [inventist.db.schema :as schema]
            [ysera.test :refer [is=]]
            [clojure.string :as str]
            [clj-time.format :as time]
            [clj-time.coerce :refer [from-date]]
            [com.walmartlabs.lacinia.schema :refer [tag-with-type]]
            [clojure.pprint :refer [pprint]]
            [inventist.util.core :as util]))

(comment "This file defines functions for interacting with the database.")

(def docker-local-uri
  "datomic:free://localhost:4334/inventist")
(def in-memory-uri
  "datomic:mem://inventist")
(def test-uri
  "datomic:mem://test")

(defn to-long [x]
  (try (Long. x)
       (catch Exception e
         (println (str "Could not convert " x " to Long: " e))
         nil)))

(defn clear-database! [uri]
  (d/delete-database uri)
  (d/create-database uri))

(defn create-fresh-test-database! []
  (clear-database! test-uri)
  (let [conn (d/connect test-uri)]
    (d/transact conn schema/entire-schema)
    conn))

(defn log-transaction-failures [tx-results]
  (as-> tx-results $
        (deref $)
        (when (= :failed (:status $))
          (clojure.pprint/pprint $)))
  tx-results)

(defn keyword?->string
  {:test (fn []
           (is= (keyword?->string :test)
                "test")
           (is= (keyword?->string 42)
                42))}
  [k]
  (if (keyword? k)
    (-> k
        (name))
    k))

(defn db-keyword->graphql-keyword
  [k]
  (-> k
      (str)
      (str/split #"/")
      last
      (str/replace "-" "_")
      keyword))

(defn pulled-result->graphql-result
  [result]
  (if (map? result)
    (->> result
         (map (fn [[k v]]
                [(db-keyword->graphql-keyword k)
                 (pulled-result->graphql-result v)]))
         (into {}))
    (keyword?->string result)))


(defn correct-person-photo-url [person]
  (if (not-empty (:photo_url person))
    (assoc person :photo_url (str (:schoolsoft_id person) ".jpg"))
    (dissoc person :photo_url)))


(defn get-person [db {person-email :person-email
                      person-eid   :person-db-id}]
  (->> (if-let [person-eid (to-long person-eid)]
         (d/pull db ["*"] person-eid)
         (when-let [person-email person-email]
           (first
             (d/q '[:find [(pull ?e ["*"]) ...]
                    :in $ ?person-email
                    :where
                    [?e :person/email ?person-email]]
                  db person-email))))
       (pulled-result->graphql-result)
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
              '[:find [(pull ?e ["*"]) ...]
                :in $ [?group ...]
                :where
                [?e :person/schoolsoft-id]
                [?e :person/groups ?group-eid]
                (or [(= ?group ?group-eid)]
                    [?group-eid :group/name ?group])]
              '[:find [(pull ?e ["*"]) ...]
                :where
                [?e :person/schoolsoft-id]])
            db
            groups)
       (map pulled-result->graphql-result)
       (map correct-person-photo-url)))

(defn query-pull-entire-inventory-item [entity-symbol]
  `(~(symbol "pull") ~entity-symbol ["*" {:com.apple.product/generation ["*"]}]))

(defn get-inventory-item
  [db {serial-number :serial-number
       id            :id}]
  {:pre [(or serial-number id)]}
  (let [id (to-long id)]
    (->> (cond id
               (d/pull db ["*" {:com.apple.product/generation ["*"]}] id)
               serial-number
               (d/pull db ["*" {:com.apple.product/generation ["*"]}] [:com.apple.product/serial-number serial-number]))
         (pulled-result->graphql-result))))

(defn query-inventory [db {search-terms :search_terms}]
  (->> (d/q (if search-terms
              '[:find [(pull ?e ["*" {:com.apple.product/generation ["*"]}]) ...]
                :in $ [?search-terms ...]
                :where
                (or [?e :com.apple.product/serial-number ?v]
                    [?e :inventory-item/model-name ?v])
                [(str "(?i)" ?search-terms) ?pattern-str]
                [(re-pattern ?pattern-str) ?pattern]
                [(re-find ?pattern ?v)]]
              '[:find [(pull ?e ["*" {:com.apple.product/generation ["*"]}]) ...]
                :where
                (or [?e :com.apple.product/serial-number]
                    [?e :inventory-item/name]
                    [?e :inventory-item/brand]
                    [?e :inventory-item/image-url])])
            db
            search-terms)
       (map pulled-result->graphql-result)))

(defn get-inventory-of-person
  [db {person-db-id :person-db-id}]
  (->> (d/q '[:find [(pull ?e ["*" {:com.apple.product/generation ["*"]}]) ...]
              :in $ ?person-eid
              :where [?e :inventory-item/user ?person-eid]]
            db
            person-db-id)
       (map pulled-result->graphql-result)
       (map (fn [result] (assoc result :class "laptop")))))

(defn get-collection-items
  [db {:keys [collection-id]}]
  (->> (d/q '[:find [(pull ?e ["*"]) ...]
              :in $ ?collection-eid
              :where [?collection-eid :collection/members ?e]]
            db
            collection-id)
       (map pulled-result->graphql-result)))


(defn- query-result->reallocation
  [[inventory-item new-user instant]]
  (tag-with-type {:inventory_item inventory-item
                  :new_user       new-user
                  :instant        (time/unparse (time/formatters :date-time-no-ms) (from-date instant))}
                 :Reallocation))


(defn get-inventory-history-of-item
  [db {id :inventory-item-db-id}]
  (->> (d/q '[:find ?inventory-item-eid ?person-eid ?instant
              :in $ ?inventory-item-eid
              :where
              [?inventory-item-eid :inventory-item/user ?person-eid ?tx true]
              [?tx :db/txInstant ?instant]]
            (d/history db)
            id)
       (map query-result->reallocation)))

(defn get-inventory-history-of-person
  [db {id :person-db-id}]
  (->> (d/q '[:find ?inventory-item-eid ?person-eid ?instant
              :in $ ?person-eid
              :where
              [?inventory-item-eid :inventory-item/user ?person-eid ?tx true]
              [?tx :db/txInstant ?instant]]
            (d/history db)
            id)
       (map query-result->reallocation)))

(defn get-group
  [db {group-eid  :group-db-id
       group-name :group-name}]
  (cond
    group-eid
    (->> (d/pull db ["*"] group-eid)
         (pulled-result->graphql-result))

    group-name
    (->> (d/q '[:find ?e
                :in $ ?group-name
                :where
                [?e :group/name ?group-name]]
              db
              group-name)
         (ffirst)
         (d/pull db '[*]))))

(defn instant-of-transact-result
  [transact-result]
  (->> @transact-result
       (:tx-data)
       (filter (fn [datom]
                 (inst? (:v datom))))
       (first)
       (:v)))

(defn set-user-of-inventory-item [conn {inventory-item-id            :inventory-item-id
                                        inventory-item-serial-number :inventory-item-serial-number
                                        new-user-id                  :new-user-id}]
  {:pre [(or inventory-item-id inventory-item-serial-number)]}
  (let [inventory-item-id (to-long inventory-item-id)
        new-user-id       (to-long new-user-id)]
    {:tx-instant (instant-of-transact-result
                   (d/transact conn [(merge (cond inventory-item-id
                                                  {:db/id inventory-item-id}
                                                  inventory-item-serial-number
                                                  {:com.apple.product/serial-number inventory-item-serial-number})
                                            {:inventory-item/user new-user-id})]))}))









