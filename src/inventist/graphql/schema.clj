(ns inventist.graphql.schema
  (:require
    [clojure.java.io :as io]
    [clojure.edn :as edn]
    [com.walmartlabs.lacinia.schema :as schema]
    [com.walmartlabs.lacinia.util :refer [attach-resolvers]]
    [inventist.db.core :as db]
    [datomic.api :as d]
    [clojure.string :as str]
    [ysera.test :refer [is=]]
    [inventist.util.core :as util]))

(comment
  "This file reads the graph-ql schema from resources/inventist-schema.edn and "
  "defines and connects the functions used to resolve the GraphQL queries.")

(defn add-photo-base-url
  {:test (fn [] (is= (add-photo-base-url {:photo_url "1.jpg"} "http://a.b")
                     {:photo_url "http://a.b/photos/1.jpg"}))}
  [person files-base-url]
  (update person :photo_url
          (fn [image-name]
            (when (not-empty image-name)
              (str/join "/"
                        [files-base-url
                         "photos"
                         (str/replace image-name
                                      #"^/" "")])))))

;; TODO: Error handling, including not found
(defn ^:private resolve-groups
  [context args person]
  (for [group (:groups person)]
    (db/get-group (d/db (:db-connection context))
                  {:group-db-id (get group ":db/id")})))

(defn ^:private resolve-person
  [context args parent]
  (if-let [person-id (or (get-in parent [:users ":db/id"])
                         (get-in parent [:new_user])
                         (Long. (:id args)))]
    (-> (db/get-person (d/db (:db-connection context)) {:person-db-id person-id})
        (add-photo-base-url (:files-base-url context)))))

(defn ^:private query-people
  [context args _value]
  (->> (db/get-people (d/db (:db-connection context)) args)
       (map (fn [person]
              (add-photo-base-url person (:files-base-url context))))))

(defn ^:private query-computers
  [context args _value]
  (db/query-inventory (d/db (:db-connection context)) args))

(defn ^:private resolve-computers
  [context args parent]
  (db/get-inventory-of-person (d/db (:db-connection context))
                              {:person-db-id (:id parent)}))

(defn ^:private resolve-computer
  [context args parent]
  (db/get-inventory-item (d/db (:db-connection context))
                         {:id
                          ; Prioritize the passed id argument over parent id.
                          (if-let [id (:id args)] (Long. id)
                                                  (:inventory_item parent))
                          :serial-number
                          (:serial_number args)}))

(defn ^:private resolve-inventory-history
  [context args parent]
  (db/get-inventory-history-of-item (d/db (:db-connection context))
                                    {:inventory-item-db-id (:id parent)}))

(defn ^:private resolve-person-history
  [context args parent]
  (db/get-inventory-history-of-person (d/db (:db-connection context))
                                      {:person-db-id (:id parent)}))

(defn inventist-schema
  []
  (-> (io/resource "inventist-schema.edn")
      slurp
      edn/read-string
      (attach-resolvers {:resolve-documents         identity ;TODO
                         :resolve-groups            resolve-groups
                         :resolve-person            resolve-person
                         :query-people              query-people
                         :resolve-person-history    resolve-person-history
                         :resolve-computer          resolve-computer
                         :resolve-computers         resolve-computers
                         :query-computers           query-computers
                         :resolve-inventory-history resolve-inventory-history})
      schema/compile))
