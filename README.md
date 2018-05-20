# Inventist Backend

A backend for the Inventist web-app.
 
Since we are using Clojure and are inherently interested in the history of each inventory item we are using the append-only Datomic database and exposing that through a GraphQL API.

## Usage

Running main.clj will start a server on port 8888. GraphQL calls can then be made to [host]:8888/graphql and there's a web GraphQL client that can be reached by pointing a browser directly at [host]:8888.
