(ns inventist.db.import
  (:require [datomic.api :as d]
            [inventist.db.core :refer [in-memory-uri clear-database! log-transaction-failures]]
            [inventist.db.schema :as schema]
            [inventist.schoolsoft.client.core :as schoolsoft]
            [inventist.datorbasen.client.core :as datorbasen]))

(defn import-fresh-database!
  [db-uri]
  (clear-database! db-uri)
  (let [db-connection (d/connect db-uri)]
    (-> (d/transact db-connection
                    (concat schema/entire-schema
                            [[:db/add "datomic.tx" :db/txInstant #inst "2015-01-01"]]))
        (log-transaction-failures))

    (-> (d/transact db-connection
                    (concat (schoolsoft/create-all-data-transaction)
                            [[:db/add "datomic.tx" :db/txInstant #inst "2015-01-01"]]))
        (log-transaction-failures))

    (-> (d/transact db-connection
                    (concat (datorbasen/create-user-and-group-modifications (d/db db-connection))
                            [[:db/add "datomic.tx" :db/txInstant #inst "2015-01-01"]]))
        (log-transaction-failures))

    (doall
      (->> (datorbasen/create-transactions-for-all-registrations (d/db db-connection))
           (map (fn [transaction]
                  (-> (d/transact db-connection transaction)
                      (log-transaction-failures))))))

    db-connection))

(comment
  (d/q '[:find ?e ?name ?lname ?computer                    ;(pull ?ce ["*"])
         :where
         [?e :person/first-name ?name]
         [?e :person/last-name ?lname]
         [?e :person/active true]
         [?e :person/groups ?group]
         [?group :group/name "7-Leoparder"]
         [?ce :inventory-item/user ?e]
         [?ce :inventory-item/model-name ?computer]
         [?ce :inventory-item/user ?users]]
       (d/db (import-fresh-database! in-memory-uri)))

  (-> (get-people (d/db (d/connect in-memory-uri)) {:groups ["7-Tigrar"]})
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
  (get-person (d/db (d/connect in-memory-uri)) {:person-email "daniel.schlaug@gripsholmsskolan.se"})
  (get-inventory-of-person (d/db (d/connect in-memory-uri)) {:person-db-id person-eid})
  (get-inventory-history-of-item (d/db (d/connect in-memory-uri)) {:inventory-item-db-id 17592186046563})
  (get-inventory-history-of-person (d/db (d/connect in-memory-uri)) {:person-db-id person-eid})
  (set-user-of-inventory-item (d/connect in-memory-uri) {:inventory-item-id 17592186046563
                                                         :new-user-id       person-eid}))
