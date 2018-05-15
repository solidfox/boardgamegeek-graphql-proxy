(ns inventist.db.core
  (:require [datomic.api :as d]))

(def docker-local-uri
  "datomic:free://localhost:4334/inventist")
(def in-memory-uri
  "datomic:mem://inventist")
(def uri
  docker-local-uri)

(d/delete-database uri)

(d/create-database uri)

(def conn (d/connect uri))

;(def cfg {:server-type :peer-server
;          :access-key ""
;          :secret ""
;          :endpoint "localhost:4334"})
;
;(def client (d/client cfg))
;
;(def conn (d/connect client {:db-name "inventist"}))

(comment
  "Reload db"
  (let [uri docker-local-uri])
  (seq (d/entity (d/db conn) 17592186045950)))


