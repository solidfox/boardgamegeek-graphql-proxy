(ns inventist.graphql.schema
  (:require
    [clojure.java.io :as io]
    [clojure.edn :as edn]
    [com.walmartlabs.lacinia.schema :as schema]
    [com.walmartlabs.lacinia.util :refer [attach-resolvers]]
    [inventist.db.core :as db]
    [datomic.api :as d]))

;; TODO: Error handling, including not found
(defn ^:private query-people
  [context args _value]
  (db/get-people (d/db (:db-connection context)) args))

(defn ^:private query-computers
  [context args _value]
  (println args)
  (db/query-inventory (d/db (:db-connection context)) args))

(defn ^:private resolve-person
  [context args parent]
  (let [person-id (or (get-in parent [:users ":db/id"])
                      (get-in parent [:new_user]))]
    (db/get-person (d/db (:db-connection context)) {:person-db-id person-id})))

(defn ^:private resolve-groups
  [context args person]
  (for [group (:groups person)]
    (db/get-group (d/db (:db-connection context))
                  {:group-db-id (get group ":db/id")})))

(defn ^:private resolve-computers
  [context args parent]
  (db/get-inventory-of-person (d/db (:db-connection context))
                              {:person-db-id (:id parent)}))

(defn ^:private resolve-inventory-history
  [context args parent]
  (db/get-inventory-history-of-item (d/db (:db-connection context))
                                    {:inventory-item-db-id (:id parent)}))

(defn inventist-schema
  []
  (-> (io/resource "inventist-schema.edn")
      slurp
      edn/read-string
      (attach-resolvers {:resolve-person            resolve-person
                         :resolve-groups            resolve-groups
                         :resolve-documents         identity
                         :resolve-inventory-history resolve-inventory-history
                         :query-people              query-people
                         :query-computers           query-computers
                         :resolve-computers         resolve-computers})
      schema/compile))
