(ns inventist.db.core
  (:require [datomic.api :as d]
            [inventist.db.schema :as schema]))

(def docker-local-uri
  "datomic:free://localhost:4334/inventist")
(def in-memory-uri
  "datomic:mem://inventist")
(def test-uri
  "datomic:mem://test")

(defn clear-database! [uri]

  (d/delete-database uri)

  (d/create-database uri))

(defn create-fresh-test-database! []
  (clear-database! [test-uri])
  (d/db (d/connect test-uri)))

(comment
  "Reload db"
  (fresh-database in-memory-uri)
  (seq (d/entity (d/db conn) 17592186045950)))


