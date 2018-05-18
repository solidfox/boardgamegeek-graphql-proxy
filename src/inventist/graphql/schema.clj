(ns inventist.graphql.schema
  (:require
    [clojure.java.io :as io]
    [clojure.edn :as edn]
    [com.walmartlabs.lacinia.schema :as schema]
    [com.walmartlabs.lacinia.util :refer [attach-resolvers]]
    [bgg-graphql-proxy.client :as client]
    [inventist.db.core :as db]
    [datomic.api :as d]))

(defn ^:private resolve-people
  [context args _value]
  ;; TODO: Error handling, including not found
  (db/get-people (d/db (:db-connection context))))

(defn ^:private resolve-groups
  [context args person]
  (for [group (:groups person)]
    (db/get-group (d/db (:db-connection context))
                  {:db-group-id (get group ":db/id")})))

(defn ^:private resolve-search
  [context args _value]
  (client/search (:cache context) (:term args)))

(defn ^:private extract-ids
  [board-game key args]
  (let [{:keys [limit]} args]
    (cond->> (get board-game key)
             limit (take limit))))

(defn ^:private resolve-game-publishers
  [context args board-game]
  (client/publishers (:cache context) (extract-ids board-game :publisher-ids args)))

(defn ^:private resolve-game-designers
  [context args board-game]
  (client/designers (:cache context) (extract-ids board-game :designer-ids args)))

(defn inventist-schema
  []
  (-> (io/resource "inventist-schema.edn")
      slurp
      edn/read-string
      (attach-resolvers {:resolve-groups                 resolve-groups
                         :resolve-documents              resolve-people
                         :resolve-inventory-history-item resolve-people
                         :resolve-people                 resolve-people
                         :resolve-computers              resolve-people
                         :resolve-search                 resolve-search
                         :resolve-game-publishers        resolve-game-publishers
                         :resolve-game-designers         resolve-game-designers})
      schema/compile))
