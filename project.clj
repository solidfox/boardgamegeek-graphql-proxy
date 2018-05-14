(defproject com.howardlewisship/bgg-graphql-proxy "0.0.1"
  :description "GraphQL interface to BoardGameGeek"
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [io.aviso/logging "0.2.0"]
                 [com.walmartlabs/lacinia "0.25.0"]
                 [clj-http "3.8.0"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/data.xml "0.0.8"]
                 [io.pedestal/pedestal.service "0.5.3"]
                 [io.pedestal/pedestal.jetty "0.5.3"]
                 [com.datomic/datomic-free "0.9.5697"]
                 [org.clojure/data.csv "0.1.4"]]
  :codox {:source-uri "https://github.com/hlship/boardgamegeek-graphql-proxy/blob/master/{filepath}#L{line}"
          :metadata {:doc/format :markdown}})
