(ns inventist.graphql.server
  (:require [inventist.db.core :as db]
            [inventist.db.import :refer [import-fresh-database!]]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.interceptor :refer [interceptor]]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [com.walmartlabs.lacinia :refer [execute]]
            [com.walmartlabs.lacinia.pedestal :as lacinia-pedestal]
            [ring.util.response :as response]
            [clojure.string :as str]
            [inventist.util.core :as util]
            [io.pedestal.interceptor.chain :as interceptor.chain]
            [io.pedestal.http.ring-middlewares :refer [multipart-params]]
            [ring.middleware.multipart-params.temp-file :refer [temp-file-store]]))

(def state-atom (atom {:db-connection (import-fresh-database! db/in-memory-uri)}))

(defn ^:private index-handler
  "Handles the index request as if it were /graphiql/index.html."
  [request]
  (response/redirect "/index.html"))

(defn http-get-variable-map
  "Reads the `variables` query parameter, which contains a JSON string
  for any and all GraphQL variables to be associated with this request.

  Returns a map of the variables (using keyword keys)."
  [request]
  (let [vars (get-in request [:query-params :variables])]
    (if-not (str/blank? vars)
      (json/read-str vars :key-fn keyword)
      {})))

(def ^:private graphql-post-handler
  {:name ::graphql-post-handler
   :enter
         (fn [{request :request
               :as     context}]
           (assoc context :request (merge request (lacinia-pedestal/extract-query request))))})



(def ^:private graphql-get-handler
  {:name  ::graphql-get-handler
   :enter (fn [{request :request
                :as     context}]
            (assoc context :request (merge request (lacinia-pedestal/extract-query request))))})

(def ^:private graphql-query-handler
  {:name  ::graphql-query-handler
   :enter (fn
            [{{query :graphql-query
               vars  :graphql-vars} :request
              graphql-context       ::graphql-context
              compiled-schema       ::graphql-schema
              :as                   context}]
            (let [result (execute compiled-schema query vars graphql-context)
                  status (if (-> result :errors seq)
                           400
                           200)]
              (assoc context :response
                             {:status  status
                              :headers {"Content-Type" "application/json"}
                              :body    (json/write-str result)})))})

(defn ^:private graphql-handler
  [compiled-schema]
  {:name  ::graphql-handler
   :enter (fn [{request :request
                :as     context}]
            (as-> context $
                  (assoc $ ::graphql-schema compiled-schema)
                  (case (:request-method request)
                    :get
                    (interceptor.chain/enqueue $ [graphql-get-handler])
                    :post
                    (cond-> $
                            (str/starts-with? (:content-type request) "multipart/form-data")
                            (interceptor.chain/enqueue*
                              (multipart-params
                                {:store (temp-file-store {:expires-in 7200})}))
                            true
                            (interceptor.chain/enqueue* lacinia-pedestal/body-data-interceptor
                                                        graphql-post-handler)))
                  (interceptor.chain/enqueue* $ graphql-query-handler)))})

(def ^:private add-base-url
  {:name  ::add-base-url
   :enter (fn [{request :request
                :as     context}]
            (assoc-in context [::graphql-context :base-url]
                      (subs (str (:scheme request) "://" (:server-name request) ":" (:server-port request))
                            1)))})

(def ^:private add-database
  {:name  ::add-database
   :enter (fn [context]
            (assoc-in context [::graphql-context :db-connection] (:db-connection @state-atom)))})

(defn ^:private routes
  [compiled-schema]
  (let [handle-graph-ql (graphql-handler compiled-schema)]
    (route/expand-routes
      #{["/" :get index-handler :route-name :graphiql-ide-index]
        ["/graphql" :post [add-base-url add-database handle-graph-ql] :route-name :graphql-post]
        ["/graphql" :get [add-base-url add-database handle-graph-ql] :route-name :graphql-get]})))

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
                                                                             "http://localhost:3450"
                                                                             "http://localhost:8888"
                                                                             "http://inventory.gripsholmsskolan.se"
                                                                             "http://backend.inventory.gripsholmsskolan.se:8888"]}}))
