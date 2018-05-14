(ns inventist.db.schema
  (:require [datomic.api :as d]
            [inventist.db.core :as core]))

(def inventory-item-schema [{:db/ident       :inventory-item/id
                             :db/valueType   :db.type/uuid
                             :db/cardinality :db.cardinality/one
                             :db/unique      :db.unique/identity}
                            {:db/ident       :inventory-item/name
                             :db/valueType   :db.type/string
                             :db/cardinality :db.cardinality/one}
                            {:db/ident       :inventory-item/release-date
                             :db/valueType   :db.type/long
                             :db/cardinality :db.cardinality/one}
                            {:db/ident       :inventory-item/brand
                             :db/valueType   :db.type/string
                             :db/cardinality :db.cardinality/one}
                            {:db/ident       :inventory-item/model-name
                             :db/valueType   :db.type/string
                             :db/cardinality :db.cardinality/one}
                            {:db/ident       :inventory-item/model-identifier
                             :db/valueType   :db.type/string
                             :db/cardinality :db.cardinality/one}
                            {:db/ident       :inventory-item/serial-number
                             :db/valueType   :db.type/string
                             :db/cardinality :db.cardinality/one
                             :db/unique      :db.unique/identity}
                            {:db/ident       :inventory-item/image-url
                             :db/valueType   :db.type/string
                             :db/cardinality :db.cardinality/one}
                            {:db/ident       :inventory-item/history
                             :db/valueType   :db.type/ref
                             :db/cardinality :db.cardinality/many}
                            {:db/ident       :inventory-item/purchase-details
                             :db/valueType   :db.type/ref
                             :db/cardinality :db.cardinality/one}
                            {:db/ident       :inventory-item/color
                             :db/valueType   :db.type/string
                             :db/cardinality :db.cardinality/one}])

(def people-group-schema [{:db/ident       :group/schoolsoft-id
                           :db/valueType   :db.type/string
                           :db/cardinality :db.cardinality/one
                           :db/unique      :db.unique/identity}
                          {:db/ident       :group/name
                           :db/valueType   :db.type/string
                           :db/cardinality :db.cardinality/one}
                          {:db/ident       :group/description
                           :db/valueType   :db.type/string
                           :db/cardinality :db.cardinality/one}
                          {:db/ident       :group/school-class
                           :db/valueType   :db.type/boolean
                           :db/cardinality :db.cardinality/one}
                          {:db/ident       :group/active
                           :db/valueType   :db.type/boolean
                           :db/cardinality :db.cardinality/one}])

(def document-schema [{:db/ident       :document/id
                       :db/valueType   :db.type/string
                       :db/cardinality :db.cardinality/one
                       :db/unique      :db.unique/identity}
                      {:db/ident       :document/file-extension
                       :db/valueType   :db.type/string
                       :db/cardinality :db.cardinality/one}
                      {:db/ident       :document/name
                       :db/valueType   :db.type/string
                       :db/cardinality :db.cardinality/one}
                      {:db/ident       :document/comment
                       :db/valueType   :db.type/string
                       :db/cardinality :db.cardinality/one}
                      {:db/ident       :document/url
                       :db/valueType   :db.type/string
                       :db/cardinality :db.cardinality/one}])

(def entire-schema (concat inventory-item-schema
                           people-group-schema
                           document-schema))

(defn update-schema! [conn schema]
  (d/transact conn schema))

(update-schema! core/conn entire-schema)

(comment
  (d/transact core/conn [{:document/id "moj"
                          :document/name "Test"}
                         {:document/id "soy"
                          :document/name "goj"}])
  (d/q '[:find ?id ?name
         :where [?e :document/id ?id]
                [?e :document/name ?name]] (d/db core/conn)))
