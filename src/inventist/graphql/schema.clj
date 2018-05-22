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
  (if (:photo_url person)
    (update person :photo_url
            (fn [image-name]
              (when (not-empty image-name)
                (str/join "/"
                          [files-base-url
                           "photos"
                           (str/replace image-name
                                        #"^/" "")]))))
    person))

;; TODO: Error handling, including not found
(defn ^:private resolve-groups
  [context args person]
  (for [group (:groups person)]
    (db/get-group (d/db (:db-connection context))
                  {:group-db-id (get group ":db/id")})))

(defn ^:private resolve-person
  [context args parent]
  (-> (if-let [person-id (or (get-in parent [:user ":db/id"])
                             (:id args))]
        (db/get-person (d/db (:db-connection context)) {:person-db-id person-id})
        (if-let [person-email (:email args)]
          (db/get-person (d/db (:db-connection context)) {:person-email person-email})))
      (add-photo-base-url (:files-base-url context))))

(defn ^:private resolve-new-user
  [context _args parent]
  (resolve-person context {:id (:new_user parent)} nil))

(defn ^:private resolve-old-user
  [context _args parent]
  (resolve-person context {:id (:old_user parent)} nil))


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
                         {:id            (or (:id args) (:inventory_item parent))
                          :serial-number (:serial_number args)}))

(defn ^:private resolve-inventory-history
  [context args parent]
  (db/get-inventory-history-of-item (d/db (:db-connection context))
                                    {:inventory-item-db-id (:id parent)}))

(defn ^:private resolve-person-history
  [context args parent]
  (db/get-inventory-history-of-person (d/db (:db-connection context))
                                      {:person-db-id (:id parent)}))

(defn ^:private set-user-of-inventory-item
  [context {inventory-item-id :inventory_item_id
            new-user-id       :new_user_id} _parent]
  (let [conn        (:db-connection context)
        old-user-id (get-in (db/get-inventory-item (d/db conn)
                                                   {:id inventory-item-id})
                            [:user ":db/id"])
        instant     (:tx-instant (db/set-user-of-inventory-item conn
                                                                {:inventory-item-id inventory-item-id
                                                                 :new-user-id       new-user-id}))]
    {:instant        instant
     :old_user       old-user-id
     :new_user       new-user-id
     :inventory_item inventory-item-id}))

(defn ^:private resolve-person-history
  [context args parent]
  (db/get-inventory-history-of-person (d/db (:db-connection context))
                                      {:person-db-id (:id parent)}))

(defn inventist-schema
  []
  (-> (io/resource "inventist-schema.edn")
      slurp
      edn/read-string
      (attach-resolvers {:resolve-documents          identity ;TODO
                         :resolve-groups             resolve-groups
                         :resolve-person             resolve-person
                         :query-people               query-people
                         :resolve-person-history     resolve-person-history
                         :resolve-computer           resolve-computer
                         :resolve-computers          resolve-computers
                         :query-computers            query-computers
                         :resolve-inventory-history  resolve-inventory-history
                         :set-user-of-inventory-item set-user-of-inventory-item
                         :resolve-new-user           resolve-new-user
                         :resolve-old-user           resolve-old-user})
      schema/compile))
