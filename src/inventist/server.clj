(ns inventist.server
  (:require [inventist.db.core :as db]
            [inventist.db.schema :as schema]
            [datomic.api :as d]
            [inventist.schoolsoft.client.core :as schoolsoft]
            [inventist.datorbasen.client.core :as datorbasen]))

(def db-uri db/in-memory-uri)

(db/clear-database! db-uri)

(def db-connection (d/connect db-uri))

(d/transact db-connection (concat schema/entire-schema
                                  [[:db/add "datomic.tx" :db/txInstant #inst "2015-01-01"]]))

(d/transact db-connection (concat (schoolsoft/create-all-data-transaction)
                                  [[:db/add "datomic.tx" :db/txInstant #inst "2015-01-01"]]))

(doall
  (->> (datorbasen/create-transactions-for-all-registrations (d/db db-connection))
       (map (fn [transaction] (d/transact db-connection transaction)))))

(comment (-> {:födelsedag                           "0000-00-00",
              :klass                                "7 Tigrar",
              :smart-status                         "SMART Status: Verified",
              :anmärkning                           "",
              :förnamn                              "Vendela",
              :fyra-sista-siffrorna-i-personnummret "XXXX",
              :ram                                  "8192",
              :serienummer                          "FVHTPSRXJ1WK",
              :mac-address                          "d4:61:9d:1b:09:d2",
              :ssd                                  "TRUE",
              :status                               "",
              :namn-på-hårddisk                     "Macintosh HD",
              :os-x-version                         "10.12.6",
              :efternamn                            "Wohrne",
              :modellnamn                           "MacBook Air (13-inch, 2017)",
              :ledig-hårddisk                       "83",
              :kan-lösenord                         "TRUE",
              :timestamp                            "2018-02-16T22:04:22",
              :diagnostik                           "",
              :modell                               "MacBookAir7,2",
              :hårddisk                             "120"}
             (datorbasen/datorbasen-registration->inventory-item (d/db db-connection))))

(d/q '[:find ?e ?name ?lname ?computer ;(pull ?ce ["*"])
       :where
       [?e :person/first-name ?name]
       [?e :person/last-name ?lname]
       [?e :person/active true]
       [?e :person/groups ?group]
       [?group :group/name "7-Leoparder"]
       [?ce :inventory-item/users ?e]
       [?ce :inventory-item/model-name ?computer]
       [?ce :inventory-item/users ?users]]
     (d/db db-connection))
