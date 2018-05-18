(ns inventist.datorbasen.client.core
  (:require [inventist.util.core :as util]
            [clojure.java.io :as io]
            [datomic.api :as d]
            [clojure.string :as string]
            [clojure.instant :refer [read-instant-date]]))


(defn invariate-text
  [text]
  (if (not (nil? text))
    (-> text
        (string/trim)
        (string/lower-case)
        (string/replace #"[^\w^å^ä^ö]" ""))
    ""))

(defn find-user-person-entity
  [db {group      :klass
       first-name :förnamn
       last-name  :efternamn}]
  (-> (d/q '[:find ?e
             :in $ ?sought-fname ?sought-lname ?sought-group
             :where
             [?e :person/first-name ?fname]
             [(inventist.datorbasen.client.core/invariate-text ?fname) ?fname-inv]
             [(= ?fname-inv ?sought-fname)]
             [?e :person/last-name ?lname]
             [(inventist.datorbasen.client.core/invariate-text ?lname) ?lname-inv]
             [(= ?lname-inv ?sought-lname)]
             [?e :person/groups ?group]
             [?group :group/name ?group-name]
             [(inventist.datorbasen.client.core/invariate-text ?group-name) ?group-name-inv]]
           db
           (invariate-text first-name)
           (invariate-text last-name)
           (invariate-text group))
      (first)
      (first)))

(defn datorbasen-registration->inventory-item [registration db]
  [[:db/add "datomic.tx" :db/txInstant (read-instant-date (:timestamp registration))]
   ;[:db/retract [:inventory-item/serial-number (:serienummer registration)] :inventory-item/users _]
   (merge {:inventory-item/brand            "Apple"
           :inventory-item/model-name       (:modellnamn registration)
           :inventory-item/model-identifier (:modell registration)
           :inventory-item/serial-number    (:serienummer registration)
           :inventory-item/color            "Silver"}
          (when-let [user-entity (find-user-person-entity db registration)]
            {:inventory-item/users user-entity}))])

(defn create-transactions-for-all-registrations
  [db]
  (->> (util/read-csv (-> "confidential/inventory/registreringar.csv"
                          io/resource
                          io/file)
                      (fn [heading]
                        (-> heading
                            (string/lower-case)
                            (string/replace #"\s+" "-"))))
       (map (fn [registration] (datorbasen-registration->inventory-item registration db)))))


(comment "Example computer"
         {:födelsedag                           "0000-00-00",
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
          :hårddisk                             "120"})
