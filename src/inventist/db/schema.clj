(ns inventist.db.schema
  (:require [datomic.api :as d]))

(def entire-schema
  [;; storage-device-attributes
   {:db/ident       :storage-device/medium-type
    :db/valueType   :db.type/keyword
    :db/cardinality :db.cardinality/one
    :db/doc         "The type of the storage medium. E g HDD or SSD."}

   {:db/ident       :storage-device/capacity
    :db/valueType   :db.type/bigint
    :db/cardinality :db.cardinality/one
    :db/doc         "The capacity of the device in bytes."}


   ;; apple-device-attributes
   {:db/ident       :com.apple.product/serial-number
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity}

   {:db/ident       :com.apple.product/generation
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc         "The model generation of the Apple product. This corresponds to the model identifier and marketing name like 'MacBook Pro (13-inch, 2016, Four Thunderbolt 3 Ports)'."}

   {:db/ident       :com.apple.product.generation/model-identifier
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity}

   {:db/ident       :com.apple.product.generation/model-name
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one}

   {:db/ident       :com.apple.product.generation/species
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/many}

   {:db/ident       :com.apple.product.generation.species/model-number
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one}


   ;; inventory-item-schema
   {:db/ident       :inventory-item/name
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one}

   {:db/ident       :inventory-item/brand
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one}

   {:db/ident       :inventory-item/image-url
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one}

   {:db/ident       :inventory-item/user
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one}

   {:db/ident       :inventory-item/color
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one}

   {:db/ident       :inventory-item/issues
    :db/isComponent true
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/many}


   ;; people-group-schema
   {:db/ident       :group/schoolsoft-id
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity}

   {:db/ident       :group/email
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/many
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
    :db/cardinality :db.cardinality/one}


   ;; person-schema
   {:db/ident       :person/person-number
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity}

   {:db/ident       :person/schoolsoft-id
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity}

   {:db/ident       :person/first-name
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one}

   {:db/ident       :person/last-name
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one}

   {:db/ident       :person/gender
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
    :db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity}

   {:db/ident       :person/phone
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one}

   {:db/ident       :person/address
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one}


   ;; file-schema
   {:db/ident       :file/id
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity}

   {:db/ident       :file/extension
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one}

   {:db/ident       :file/name
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one}

   {:db/ident       :file/comment
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one}

   {:db/ident       :file/url
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one}


   ;; issue-attributes
   {:db/ident       :item.issue/category
    :db/valueType   :db.type/keyword
    :db/cardinality :db.cardinality/one
    :db/doc         "The category of the issue. E.g. :battery-problems, :physical-damage, :water-damage etc."}

   {:db/ident       :item.issue/description
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc         "A detailed description of the issue."}

   {:db/ident       :item.issue/cause
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc         "The suspected cause of the issue. E.g. Unknown, Water exposure, Accident, Factory fault"}

   {:db/ident       :item.issue/photos
    :db/valueType   :db.type/ref                            ;To photo files
    :db/cardinality :db.cardinality/many}


   ;; collection-attributes
   {:db/ident       :collection/name
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one}

   {:db/ident       :collection/members
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/many
    :db/doc         "A detailed description of the issue."}])
