(ns inventist.test
  (:require clojure.test
            inventist.datorbasen.client.core
            inventist.db.core
            inventist.db.schema
            inventist.graphql.schema
            inventist.graphql.server
            inventist.schoolsoft.client.core
            inventist.schoolsoft.client.examples
            inventist.util.core))

(clojure.test/run-all-tests #"inventist.*")
