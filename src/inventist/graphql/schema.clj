(ns inventist.graphql.schema
  (:require
    [clojure.java.io :as io]
    [clojure.edn :as edn]
    [com.walmartlabs.lacinia.schema :as schema :refer [tag-with-type]]
    [com.walmartlabs.lacinia.util :refer [attach-resolvers]]
    [inventist.db.core :as db]
    [datomic.api :as d]
    [clojure.string :as str]
    [ysera.test :refer [is=]]
    [inventist.util.core :as util]
    [clj-time.format :as time]
    [clj-time.coerce :refer [from-date]]))

(comment
  "This file reads the graph-ql schema from resources/inventist-schema.edn and "
  "defines and connects the functions used to resolve the GraphQL queries.")

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
      (str/replace #"^:" "")
      (str/split #"/")
      last
      (str/replace "-" "_")
      keyword))

(defn graphql-args->clojure-args [graphql-args]
  (->> graphql-args
       (map (fn [[k v]] [(-> k
                             (str) (subs 1)
                             (str/replace #"_" "-")
                             keyword)
                         v]))
       (into {})))

(defn pulled-result->graphql-result
  {:test (fn [] (is= (pulled-result->graphql-result {:ns/name :test
                                                     :vector  [{":ns/name" :test}]
                                                     :nested  {":ns/name" :test}})
                     {:name   "test"
                      :vector [{:name "test"}]
                      :nested {:name "test"}}))}
  [result]
  (cond (map? result)
        (->> result
             (map (fn [[k v]]
                    [(db-keyword->graphql-keyword k)
                     (pulled-result->graphql-result v)]))
             (into {}))
        (or (list? result)
            (vector? result))
        (->> result
             (map pulled-result->graphql-result))
        :else
        (keyword?->string result)))

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
    (pulled-result->graphql-result
      (db/get-group (d/db (:db-connection context))
                    {:group-db-id (:id group)}))))

(defn ^:private db-person->graphql-person [base-url db-person]
  (-> db-person
      (pulled-result->graphql-result)
      (add-photo-base-url base-url)))


(defn ^:private resolve-person
  [{db-connection :db-connection
    base-url      :base-url} args parent]
  (->> (if-let [person-id (or (get-in parent [:user :id])
                              (:id args))]
         (db/get-person (d/db db-connection) {:person-db-id person-id})
         (if-let [person-email (:email args)]
           (db/get-person (d/db db-connection) {:person-email person-email})))
       (db-person->graphql-person base-url)))

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
       (map (partial db-person->graphql-person base-url))))


(defn db-computer->graphql-computer
  {:test (fn [] (is= (db-computer->graphql-computer {:com.apple.product/generation {:com.apple.product.generation/model-name "macbook"}
                                                     :product/name                 "test"})
                     {:model_name "macbook"
                      :name       "test"
                      :class      "laptop"}))}
  [db-computer]
  (-> (merge (:com.apple.product/generation db-computer)
             (dissoc db-computer :com.apple.product/generation))
      (pulled-result->graphql-result)
      (assoc :class "laptop")))

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
  (-> (db/get-collection-items (d/db (:db-connection context))
                               {:collection-id (:id collection)})
      (pulled-result->graphql-result)))

(defn ^:private resolve-computer
  [context args parent]
  (-> (db/get-inventory-item (d/db (:db-connection context))
                             {:id            (or (:id args) (:inventory_item parent))
                              :serial-number (:serial_number args)})
      db-computer->graphql-computer))

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

(defn ^:private add-inventory-item-issue-report
  [context
   {item_id     :item_id
    category    :category
    description :description
    cause       :cause
    :as         issue-report}
   _parent]
  (let [conn           (:db-connection context)
        inventory-item (db/get-inventory-item (d/db conn)
                                              {:id item_id})]
    (-> (db/add-inventory-item-issue-report conn {:item-id     item_id
                                                  :category    category
                                                  :description description
                                                  :cause       cause})
        (pulled-result->graphql-result))))

(defn ^:private query-result->reallocation
  [[inventory-item new-user instant]]
  (tag-with-type {:inventory_item inventory-item
                  :new_user       new-user
                  :instant        (time/unparse (time/formatters :date-time-no-ms) (from-date instant))}
                 :Reallocation))

(defn ^:private resolve-inventory-history
  [context args parent]
  (->> (db/get-inventory-history-of-item (d/db (:db-connection context))
                                         {:inventory-item-db-id (:id parent)})
       (map query-result->reallocation)))

(defn ^:private resolve-person-history
  [context _args parent]
  (->> (db/get-inventory-history-of-person (d/db (:db-connection context))
                                           {:person-db-id (:id parent)})
       (map query-result->reallocation)))

(defn ^:private get-collections
  [context _args _parent]
  (-> (db/get-collections (d/db (:db-connection context)))
      (pulled-result->graphql-result)))

(defn ^:private add-collection
  [context args _parent]
  (-> (db/add-collection (:db-connection context) (graphql-args->clojure-args args))
      (pulled-result->graphql-result)))

(defn ^:private remove-collection
  [context args _parent]
  {:success (not= false (db/remove-collection (:db-connection context) (graphql-args->clojure-args args)))})

(defn ^:private edit-collection-metadata
  [context args _parent]
  (-> (db/edit-collection-metadata (:db-connection context) {:collection-id (:collection_id args)
                                                             :new-metadata  {:name (:name args)}})
      (pulled-result->graphql-result)))

(defn ^:private add-entities-to-collection
  [context args _parent]
  {:success (db/add-entities-to-collection (:db-connection context) {:collection-id (:collection_id args)
                                                                     :entity-ids    (:entity_ids args)})})

(defn ^:private remove-entities-from-collection
  [context args _parent]
  (db/remove-entities-from-collection (:db-connection context) {:collection-id (:collection_id args)
                                                                :new-metadata  {:name (:name args)}}))

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
                         :report_issue_with_inventory_item add-inventory-item-issue-report
                         :resolve-inventory-history        resolve-inventory-history
                         :set-user-of-inventory-item       set-user-of-inventory-item
                         :resolve-new-user                 resolve-new-user
                         :resolve-old-user                 resolve-old-user
                         :collections                      get-collections
                         :add-collection                   add-collection
                         :remove-collection                remove-collection
                         :edit-collection-metadata         edit-collection-metadata
                         :add-entities-to-collection       add-entities-to-collection
                         :remove-entities-from-collection  remove-entities-from-collection
                         :resolve-collection-items         resolve-collection-items})
      schema/compile))
