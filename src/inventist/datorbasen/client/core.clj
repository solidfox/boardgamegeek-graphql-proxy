(ns inventist.datorbasen.client.core
  (:require [inventist.util.core :as util]
            [clojure.java.io :as io]
            [datomic.api :as d]
            [clojure.string :as string]
            [clojure.instant :refer [read-instant-date]]
            [clj-fuzzy.metrics :refer [levenshtein]]
            [ysera.test :as test]
            [clojure.string :as str]
            [clojure.math.numeric-tower :refer [expt]]
            [inventist.db.core :as db]))

(comment "Here inventory data is converted to Datomic transactions from tsv files that have been redacted from"
         "this git-repo for natural reasons.")

(defn invariate-text
  [text]
  (if (not (nil? text))
    (-> text
        (string/trim)
        (string/lower-case)
        (string/replace #"[^a-z^å^ä^ö]" ""))
    ""))

(defn normalized-levenshtein
  {:test (fn []
           (test/is= (normalized-levenshtein "Dybro " "Dybro")
                     (/ 1 6))
           (test/is= (normalized-levenshtein "Hej" "Proo")
                     (/ 4 4))
           (test/is= (normalized-levenshtein "Hej" "Hej")
                     0))}
  [s1 s2]
  (/ (levenshtein s1 s2)
     (max (count s1)
          (count s2))))

(defn sufficiently-similar
  {:test (fn [] (test/is (sufficiently-similar "Dybro Jörgensen Hammild"
                                               "Dybro" 0.3)))}
  [candidate-text sought-text threshold]
  (let [candidate-text (invariate-text candidate-text)
        sought-text    (invariate-text sought-text)]
    (or (> threshold (normalized-levenshtein candidate-text sought-text))
        (str/includes? candidate-text sought-text)
        (str/includes? sought-text candidate-text))))

(defn sufficient-group
  [candidate-group sought-group]
  (or (empty? candidate-group)
      (empty? sought-group)
      (= (invariate-text sought-group)
         (invariate-text candidate-group))))

(defn best-match [db registration person-entity-id-set]
  (->> person-entity-id-set
       (map first)
       (d/pull-many db '[:person/first-name
                         :person/last-name
                         :db/id
                         {:person/groups [:group/name]}])
       (sort-by (fn [person]
                  (+ (expt (- 1 (normalized-levenshtein (:person/first-name person)
                                                        (:förnamn registration)))
                           2)
                     (expt (- 1 (normalized-levenshtein (:person/last-name person)
                                                        (:efternamn registration)))
                           2)
                     (if (->> (:person/groups person)
                              (map :group/name)
                              (filter (fn [candidate-group] (sufficient-group candidate-group (:klass registration))))
                              (count)
                              (< 1))
                       0.75
                       0))))
       (reverse)
       (first)
       :db/id))


(defn find-user-person-id
  [db {group      :klass
       first-name :förnamn
       last-name  :efternamn
       :as        registration}]
  (->> (d/q '[:find ?e
              :in $ ?sought-fname ?sought-lname ?sought-group
              :where
              [?e :person/first-name ?fname]
              [(inventist.datorbasen.client.core/sufficiently-similar ?fname ?sought-fname 0.5)]
              [?e :person/last-name ?lname]
              [(inventist.datorbasen.client.core/sufficiently-similar ?lname ?sought-lname 0.5)]]
            db
            (invariate-text first-name)
            (invariate-text last-name)
            (invariate-text group))
       (best-match db registration)
       ((fn [match]
          (when (not match) (println "\nWarning: No user found for registration:")
                            (clojure.pprint/pprint registration))
          match))))

(defn clean-up-registration-to-user-it [registration]
  (if (or (= (:klass registration) "IT")
          (= (:förnamn registration) "IT")
          (= (:efternamn registration) "IT"))
    (assoc registration :förnamn "IT"
                        :efternamn "Department"
                        :klass "Avdelningar")
    registration))

(defn datorbasen-registration->inventory-item [registration db]
  (let [registration              (clean-up-registration-to-user-it registration)
        model-serialnumber-ending (as-> (:serienummer registration) $
                                        (str/trim $)
                                        (subs $ (- (count $) 4) (count $)))
        model-identifier          (str/trim (:modell registration))
        model-name                (str/trim (:modellnamn registration))
        model-generation-tempid   model-identifier]
    (remove nil?
            [[:db/add "datomic.tx" :db/txInstant (read-instant-date (:timestamp registration))]
             ;[:db/retract [:inventory-item/serial-number (:serienummer registration)] :inventory-item/users _]
             (merge {:db/id                                                  model-generation-tempid
                     :com.apple.product.generation/model-serialnumber-ending model-serialnumber-ending}
                    (when (not-empty model-identifier)
                      {:com.apple.product.generation/model-identifier model-identifier})
                    (when (not-empty model-name)
                      {:com.apple.product.generation/model-name model-name}))
             (merge {:inventory-item/brand            "Apple"
                     :com.apple.product/generation    model-generation-tempid
                     :com.apple.product/serial-number (:serienummer registration)
                     :inventory-item/color            "Silver"}
                    (when-let [user-entity (find-user-person-id db registration)]
                      {:inventory-item/user user-entity}))])))

(defn create-user-and-group-modifications
  [db]
  [{:person/first-name "Godtycklig"
    :person/last-name  "Uggla"
    :person/occupation :student
    :person/groups     [(:db/id (first (db/query-groups db {:group-name "4-Ugglor"})))]
    :person/active     false}])

(defn create-transactions-for-all-registrations
  [db]
  (->> (util/read-csv (-> "confidential/inventory/registreringar.csv"
                          io/resource
                          io/file)
                      (fn [heading]
                        (-> heading
                            (string/lower-case)
                            (string/replace #"\s+" "-")
                            (string/replace #"[^\w]*time" "timestamp"))))
       (sort-by (fn [registration] (:timestamp registration)))
       (map (fn [registration] (datorbasen-registration->inventory-item registration db)))))


(comment "Example registration"
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
