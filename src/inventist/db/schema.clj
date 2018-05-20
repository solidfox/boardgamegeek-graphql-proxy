(ns inventist.db.schema
  (:require [datomic.api :as d]
            [datomic-schema.schema :as ds]))

(def inventory-item-schema [{:db/ident       :inventory-item/name
                             :db/valueType   :db.type/string
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

                            {:db/ident       :inventory-item/users
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

(def person-schema [{:db/ident       :person/schoolsoft-id
                     :db/valueType   :db.type/string
                     :db/cardinality :db.cardinality/one
                     :db/unique      :db.unique/identity}

                    {:db/ident       :person/first-name
                     :db/valueType   :db.type/string
                     :db/cardinality :db.cardinality/one}

                    {:db/ident       :person/last-name
                     :db/valueType   :db.type/string
                     :db/cardinality :db.cardinality/one}

                    {:db/ident       :person/occupation
                     :db/valueType   :db.type/keyword
                     :db/cardinality :db.cardinality/one}

                    {:db/ident       :person/groups
                     :db/valueType   :db.type/ref
                     :db/cardinality :db.cardinality/many}

                    {:db/ident       :person/active
                     :db/valueType   :db.type/boolean
                     :db/cardinality :db.cardinality/one}

                    {:db/ident       :person/photo-url
                     :db/valueType   :db.type/string
                     :db/cardinality :db.cardinality/one}

                    {:db/ident       :person/email
                     :db/valueType   :db.type/string
                     :db/cardinality :db.cardinality/many
                     :db/unique      :db.unique/identity}

                    {:db/ident       :person/username
                     :db/valueType   :db.type/string
                     :db/unique      :db.unique/identity
                     :db/cardinality :db.cardinality/one}

                    {:db/ident       :person/phone
                     :db/valueType   :db.type/string
                     :db/cardinality :db.cardinality/one}

                    {:db/ident       :person/address
                     :db/valueType   :db.type/string
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
                           person-schema
                           people-group-schema
                           document-schema))
