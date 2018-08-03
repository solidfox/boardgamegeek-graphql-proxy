(ns inventist.db.core
  (:require [datomic.api :as d]
            [inventist.db.schema :as schema]
            [ysera.test :refer [is= is-not is]]
            [clojure.string :as str]
            [clj-time.format :as time]
            [clojure.pprint :refer [pprint]]
            [inventist.util.core :as util]))

(comment "This file defines functions for interacting with the database.")

(def docker-local-uri
  "datomic:free://localhost:4334/inventist")
(def in-memory-uri
  "datomic:mem://inventist")

(defn to-long [x]
  (try (Long. x)
       (catch Exception e
         (println (str "Could not convert " x " to Long: " e))
         nil)))

(defn clear-database! [uri]
  (d/delete-database uri)
  (d/create-database uri))

(def test-db-tx-instant #inst "2015-12-31")

(defn create-test-database! []
  (let [uri  (str "datomic:mem://test" (rand-int (Integer/MAX_VALUE)))
        _    (d/create-database uri)
        conn (d/connect uri)]
    (d/transact conn (concat [{:db/id "datomic.tx"
                               :db/txInstant test-db-tx-instant}]
                             schema/entire-schema))
    (as-> (d/transact conn
                      [{:db/id        "datomic.tx"
                        :db/txInstant test-db-tx-instant}
                       {:db/id             "lisa"
                        :person/first-name "Lisa"}
                       {:db/id             "per"
                        :person/first-name "Per"}
                       {:db/id               "macbook"
                        :inventory-item/name "macbook"
                        :inventory-item/user "lisa"}
                       {:db/id               "iphone"
                        :inventory-item/name "iphone"}
                       {:db/id               "ipad"
                        :inventory-item/name "ipad"}
                       {:db/id              "collection"
                        :collection/name    "collection"
                        :collection/members [{:db/id "macbook"}]}]) $
          (deref $)
          [conn (:tempids $)])))


(defn log-transaction-failures [tx-results]
  (as-> tx-results $
        (deref $)
        (when (= :failed (:status $))
          (clojure.pprint/pprint $)))
  tx-results)

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
                  db person-email))))))

(defn get-people
  {:test (fn [] (is=
                  (let [[conn id-map] (create-test-database!)]
                    (->> (get-people (d/db conn))
                         (map (fn [person] (dissoc person :db/id)))
                         (into #{})))
                  #{{:person/first-name "Lisa"}
                    {:person/first-name "Per"}}))}
  [db & [{groups :groups}]]
  (->> (d/q (if groups
              '[:find [(pull ?e [*]) ...]
                :in $ [?group ...]
                :where
                [?e :person/groups ?group-eid]
                (or [(= ?group ?group-eid)]
                    [?group-eid :group/name ?group])]
              '[:find [(pull ?e [*]) ...]
                :where
                [?s :db/valueType]
                [?s :db/ident ?attr]
                [(datomic.Util/namespace ?attr) ?namespace]
                [(= "person" ?namespace)]
                [?e ?attr]])
            db
            groups)))

(defn get-inventory-item
  [db {serial-number :serial-number
       id            :id}]
  {:pre [(or serial-number id)]}
  (let [id (to-long id)]
    (->> (cond id
               (d/pull db '[* {:com.apple.product/generation [*]}] id)
               serial-number
               (d/pull db '[* {:com.apple.product/generation [*]}] [:com.apple.product/serial-number serial-number])))))

(defn query-inventory [db {search-terms :search_terms}]
  (->> (d/q (if search-terms
              '[:find [(pull ?e [* {:com.apple.product/generation [*]}]) ...]
                :in $ [?search-terms ...]
                :where
                (or [?e :com.apple.product/serial-number ?v]
                    [?e :inventory-item/model-name ?v])
                [(str "(?i)" ?search-terms) ?pattern-str]
                [(re-pattern ?pattern-str) ?pattern]
                [(re-find ?pattern ?v)]]
              '[:find [(pull ?e [* {:com.apple.product/generation [*]}]) ...]
                :where
                (or [?e :com.apple.product/serial-number]
                    [?e :inventory-item/name]
                    [?e :inventory-item/brand]
                    [?e :inventory-item/image-url])])
            db
            search-terms)))

(defn get-inventory-of-person
  [db {person-db-id :person-db-id}]
  (->> (d/q '[:find [(pull ?inventory-item [* {:com.apple.product/generation [*]}]) ...]
              :in $ ?person-eid
              :where [?inventory-item :inventory-item/user ?person-eid]]
            db
            person-db-id)))

(defn get-collection-items
  [db {:keys [collection-id]}]
  (->> (d/q '[:find [(pull ?e [*]) ...]
              :in $ ?collection-eid
              :where [?collection-eid :collection/members ?e]]
            db
            collection-id)))

(defn get-group
  [db {group-eid :group-db-id}]
  group-eid
  (d/pull db '[*] group-eid))

(defn query-groups [db {:keys [group-name]}]
  (->> (d/q '[:find [(pull ?e [*]) ...]
              :in $ ?group-name
              :where
              [?e :group/name ?group-name]]
            db
            group-name)))

(defn instant-of-transact-result
  [transact-result]
  (->> transact-result
       (deref)
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

(defn get-inventory-history-of-item
  {:test (fn [] (let [[conn id-map] (create-test-database!)
                      macbook-id (get id-map "macbook")
                      lisa-id    (get id-map "lisa")]
                  (is= (get-inventory-history-of-item (d/db conn) {:inventory-item-db-id macbook-id})
                       #{[macbook-id lisa-id test-db-tx-instant]})))}
  [db {id :inventory-item-db-id}]
  (d/q '[:find ?inventory-item-eid ?person-eid ?instant
         :in $ ?inventory-item-eid
         :where
         [?inventory-item-eid :inventory-item/user ?person-eid ?tx true]
         [?tx :db/txInstant ?instant]]
       (d/history db)
       id))

(defn get-inventory-history-of-person
  {:test (fn [] (let [[conn id-map] (create-test-database!)
                      macbook-id (get id-map "macbook")
                      lisa-id    (get id-map "lisa")]
                  (is= (get-inventory-history-of-person (d/db conn) {:person-db-id lisa-id})
                       #{[macbook-id lisa-id test-db-tx-instant]})))}
  [db {id :person-db-id}]
  (d/q '[:find ?inventory-item-eid ?person-eid ?instant
         :in $ ?person-eid
         :where
         [?inventory-item-eid :inventory-item/user ?person-eid ?tx true]
         [?tx :db/txInstant ?instant]]
       (d/history db)
       id))

(defn add-inventory-item-issue-report
  {:test (fn [] (let [[conn id-map] (create-test-database!)]
                  (is= (dissoc (add-inventory-item-issue-report
                                 conn
                                 {:item-id     (get id-map "macbook")
                                  :category    :physical-damage
                                  :description "test"})
                               :db/id)
                       {:item.issue/category    :physical-damage
                        :item.issue/description "test"})))}
  [conn {item_id     :item_id
         category    :category
         description :description
         cause       :cause}]
  (let [issue-id      "temp-issue-id"
        tx-result     (deref
                        (d/transact conn [(merge {:db/id               issue-id
                                                  :item.issue/category category}
                                                 (when (not-empty description)
                                                   {:item.issue/description description})
                                                 (when (not-empty cause)
                                                   {:item.issue/cause cause}))
                                          {:db/id                 item_id
                                           :inventory-item/issues issue-id}]))
        new-issue-eid (get-in tx-result [:tempids issue-id])]
    (d/pull (:db-after tx-result) '[*] new-issue-eid)))

(defn get-collections
  {:test (fn [] (let [[conn id-map] (create-test-database!)
                      test-collection-id (get id-map "collection")]
                  (is= (->> (get-collections (d/db conn))
                            (map (fn [col] (select-keys col [:db/id :collection/name]))))
                       [{:db/id           test-collection-id
                         :collection/name "collection"}])))}
  [db]
  (d/q '[:find [(pull ?collection-eid [*]) ...]
         :where
         (or [?collection-eid :collection/members]
             [?collection-eid :collection/name])]
       db))

(defn add-collection
  {:test (fn [] (let [[conn id-map] (create-test-database!)
                      test-collection-id  (get id-map "collection")
                      added-collection-id (:db/id (add-collection conn {:name "added-collection"}))]
                  (is= (->> (get-collections (d/db conn))
                            (map (fn [collection] (select-keys collection [:db/id :collection/name])))
                            (into #{}))
                       #{{:db/id           test-collection-id
                          :collection/name "collection"}
                         {:db/id           added-collection-id
                          :collection/name "added-collection"}})))}
  [conn {name :name}]
  (let [collection-eid "new-collection-tempid"
        tx-result      (deref
                         (d/transact conn [{:db/id           collection-eid
                                            :collection/name name}]))]
    (d/pull (:db-after tx-result) '[*] (get-in tx-result [:tempids collection-eid]))))


(defn remove-collection
  {:test (fn [] (let [[conn id-map] (create-test-database!)
                      test-collection-id (:db/id (add-collection conn {:name "test-collection"}))]
                  (is-not (remove-collection
                            conn
                            {:collection-id 0}))
                  (is (remove-collection
                        conn
                        {:collection-id test-collection-id}))))}
  [conn {collection-id :collection-id}]
  (try (deref
         (d/transact conn [[:db.fn/retractEntity collection-id]]))
       (catch Exception e
         false)))

(defn add-entities-to-collection
  {:test (fn [] (let [[conn id-map] (create-test-database!)
                      test-collection-id (get id-map "collection")
                      tx-result          (add-entities-to-collection conn {:collection-id test-collection-id
                                                                           :entity-ids    [(get id-map "ipad")
                                                                                           (get id-map "iphone")]})]
                  (is= (->> (get-collection-items (d/db conn)
                                                  {:collection-id test-collection-id})
                            (map (fn [res] (select-keys res [:inventory-item/name])))
                            (into #{}))
                       #{{:inventory-item/name "macbook"}
                         {:inventory-item/name "iphone"}
                         {:inventory-item/name "ipad"}})))}
  [conn {:keys [collection-id
                entity-ids]}]
  (d/transact conn [{:db/id              collection-id
                     :collection/members entity-ids}]))

(defn remove-entities-from-collection
  {:test (fn [] (let [[conn id-map] (create-test-database!)
                      test-collection-id (get id-map "collection")
                      _                  (add-entities-to-collection conn {:collection-id test-collection-id
                                                                           :entity-ids    [(get id-map "iphone")
                                                                                           (get id-map "ipad")]})
                      tx-result          (remove-entities-from-collection conn {:collection-id test-collection-id
                                                                                :entity-ids    [(get id-map "macbook")
                                                                                                (get id-map "iphone")]})]
                  (is= (->> (get-collection-items (d/db conn)
                                                  {:collection-id test-collection-id})
                            (map (fn [res] (select-keys res [:inventory-item/name]))))
                       [{:inventory-item/name "ipad"}])))}
  [conn {:keys [collection-id
                entity-ids]}]
  (d/transact conn (for [eid entity-ids]
                     [:db/retract collection-id
                      :collection/members eid])))

(defn edit-collection-metadata
  [conn {:keys [collection-id new-metadata]}]
  (let [tx-result (d/transact conn (merge {:db/id collection-id}
                                          new-metadata))]
    (d/pull (:db-after tx-result) '[*] collection-id)))












