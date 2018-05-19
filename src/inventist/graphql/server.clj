(ns inventist.graphql.server
  (:require [inventist.db.core :as db]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.interceptor :refer [interceptor]]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [com.walmartlabs.lacinia :refer [execute]]
            [ring.util.response :as response]
            [clojure.string :as str]))

(defn ^:private index-handler
  "Handles the index request as if it were /graphiql/index.html."
  [request]
  (response/redirect "/index.html"))

(defn variable-map
  "Reads the `variables` query parameter, which contains a JSON string
  for any and all GraphQL variables to be associated with this request.

  Returns a map of the variables (using keyword keys)."
  [request]
  (let [vars (get-in request [:query-params :variables])]
    (if-not (str/blank? vars)
      (json/read-str vars :key-fn keyword)
      {})))

(defn extract-query
  [request]
  (case (:request-method request)
    :get (get-in request [:query-params :query])
    :post (slurp (:body request))
    :else ""))

(defn ^:private graphql-handler
  "Accepts a GraphQL query via GET or POST, and executes the query.
  Returns the result as text/json."
  [compiled-schema]
  (let [app-context {:db-connection (db/import-fresh-database! db/in-memory-uri)
                 :cache (atom {})}]
    (fn [request]
      (let [vars   (variable-map request)
            query  (extract-query request)
            request-base-url (subs (str (:scheme request) "://" (:server-name request) ":" (:server-port request)) 1)
            request-context (assoc app-context :files-base-url request-base-url)
            result (execute compiled-schema query vars request-context)
            status (if (-> result :errors seq)
                     400
                     200)]
        {:status  status
         :headers {"Content-Type" "application/json"}
         :body    (json/write-str result)}))))

(defn ^:private routes
  [compiled-schema]
  (let [query-handler (graphql-handler compiled-schema)]
    (route/expand-routes
      #{["/" :get index-handler :route-name :graphiql-ide-index]
        ["/graphql" :post query-handler :route-name :graphql-post]
        ["/graphql" :get query-handler :route-name :graphql-get]})))

(defn pedestal-server
  "Creates and returns server instance, ready to be started."
  [compiled-schema]
  (http/create-server {:env                   :dev
                       ::http/routes          (routes compiled-schema)
                       ::http/resource-path   "graphiql"
                       ::http/port            8888
                       ::http/type            :jetty
                       ::http/join?           false
                       ::http/allowed-origins {:creds true :allowed-origins ["http://localhost:3449"
                                                                             "http://inventory.gripsholmsskolan.se"
                                                                             "http://backend.inventory.gripsholmsskolan.se:8888"]}}))
