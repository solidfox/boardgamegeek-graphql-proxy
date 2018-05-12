(ns bgg-graphql-proxy.main
  (:require
    [io.pedestal.http :as http]
    [com.walmartlabs.lacinia :refer [execute]]
    [bgg-graphql-proxy.schema :refer [inventist-schema]]
    [bgg-graphql-proxy.server :refer [pedestal-server]]))

(defn stop-server
  [server]
  (http/stop server)
  nil)

(defn start-server
  "Creates and starts Pedestal server, ready to handle Graphql (and Graphiql) requests."
  []
  (-> (inventist-schema)
      pedestal-server
      http/start))

(comment
  "Run the below and navigate to localhost:8888 or direct a GraphQL client
  to http://localhost:8888/graphql using the GET method."
  (start-server))
