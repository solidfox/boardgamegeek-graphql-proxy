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
                  {:group-db-id (:id group)})))

(defn ^:private resolve-person
  [{db-connection :db-connection
    base-url      :base-url} args parent]
  (-> (if-let [person-id (or (get-in parent [:user :id])
                             (:id args))]
        (db/get-person (d/db db-connection) {:person-db-id person-id})
        (if-let [person-email (:email args)]
          (db/get-person (d/db db-connection) {:person-email person-email})))
      (add-photo-base-url base-url)))

(defn ^:private resolve-new-user
  [context _args parent]
  (resolve-person context {:id (:new_user parent)} nil))

(defn ^:private resolve-old-user
  [context _args parent]
  (resolve-person context {:id (:old_user parent)} nil))


(defn ^:private query-people
  [{db-connection :db-connection
    base-url      :base-url} args _value]
  (->> (db/get-people (d/db db-connection) args)
       (map (fn [person]
              (add-photo-base-url person base-url)))))

(defn db-computer->graphql-computer [db-computer]
  (merge (:generation db-computer)
         (dissoc db-computer :generation)))

(defn ^:private query-computers
  [context args _value]
  (->> (db/query-inventory (d/db (:db-connection context)) args)
       (map db-computer->graphql-computer)))

(defn ^:private resolve-computers
  [context args parent]
  (->> (db/get-inventory-of-person (d/db (:db-connection context))
                                   {:person-db-id (:id parent)})
       (map db-computer->graphql-computer)))

(defn ^:private resolve-collection-items
  [context _args collection]
  (db/get-collection-items (d/db (:db-connection context))
                           {:collection-id (:id collection)}))

(defn ^:private resolve-computer
  [context args parent]
  (-> (db/get-inventory-item (d/db (:db-connection context))
                             {:id            (or (:id args) (:inventory_item parent))
                              :serial-number (:serial_number args)})
      db-computer->graphql-computer))

(defn ^:private resolve-inventory-history
  [context args parent]
  (db/get-inventory-history-of-item (d/db (:db-connection context))
                                    {:inventory-item-db-id (:id parent)}))

(defn ^:private resolve-person-history
  [context args parent]
  (db/get-inventory-history-of-person (d/db (:db-connection context))
                                      {:person-db-id (:id parent)}))

(defn ^:private set-user-of-inventory-item
  [context {inventory-item-id            :inventory_item_id
            inventory-item-serial-number :inventory_item_serial_number
            new-user-id                  :new_user_id} _parent]
  (let [conn           (:db-connection context)
        inventory-item (db/get-inventory-item (d/db conn)
                                              {:id            inventory-item-id
                                               :serial-number inventory-item-serial-number})
        old-user-id    (get-in inventory-item [:user :id])]
    {:instant        (:tx-instant
                       (db/set-user-of-inventory-item conn
                                                      {:inventory-item-id            inventory-item-id
                                                       :inventory-item-serial-number inventory-item-serial-number
                                                       :new-user-id                  new-user-id}))
     :old_user       old-user-id
     :new_user       new-user-id
     :inventory_item (:id inventory-item)}))

(defn ^:private report_issue_with_inventory_item
  [context
   {item_id     :item_id
    category    :category
    description :description
    cause       :cause
    photos      :photos}
   _parent]
  (let [conn           (:db-connection context)
        inventory-item (db/get-inventory-item (d/db conn)
                                              {:id item_id})]
    ; TODO
    {:id               nil
     :inventory_item   inventory-item
     :reporting_person nil
     :category         nil
     :description      nil
     :cause            nil
     :photos           nil}))

(defn ^:private resolve-person-history
  [context args parent]
  (db/get-inventory-history-of-person (d/db (:db-connection context))
                                      {:person-db-id (:id parent)}))

(defn inventist-schema
  []
  (-> (io/resource "inventist-schema.edn")
      slurp
      edn/read-string
      (attach-resolvers {:resolve-documents                identity ;TODO
                         :resolve-groups                   resolve-groups
                         :resolve-person                   resolve-person
                         :query-people                     query-people
                         :resolve-person-history           resolve-person-history
                         :resolve-computer                 resolve-computer
                         :resolve-computers                resolve-computers
                         :query-computers                  query-computers
                         :report_issue_with_inventory_item report_issue_with_inventory_item
                         :resolve-inventory-history        resolve-inventory-history
                         :set-user-of-inventory-item       set-user-of-inventory-item
                         :resolve-new-user                 resolve-new-user
                         :resolve-old-user                 resolve-old-user
                         :resolve-collection-items         resolve-collection-items})
      schema/compile))
