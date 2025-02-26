(ns rinha.main-test
  (:require [cheshire.core :as json]
            [clojure.test :refer [deftest is]]
            [clojure.java.io :as io]
            [io.pedestal.http :as http]
            [io.pedestal.test :refer [response-for]]
            [next.jdbc :as jdbc]
            [rinha.main :as rinha]))


;; start a psql:
;; docker run -e POSTGRES_PASSWORD=postgres --rm -p 5432:5432 postgres:alpine

(defn setup-database
  [migrations]
  (let [{::rinha/keys [conn]
         ::http/keys  [service-fn]} (-> {::rinha/jdbc-url "jdbc:postgres://localhost:5432/postgres?user=postgres&password=postgres"}
                                        rinha/default-interceptors
                                        http/dev-interceptors
                                        http/create-servlet)]
    (doseq [migration migrations]
      (jdbc/execute! conn [migration]))
    service-fn))

(def ^:private migrations
  ["DROP TABLE IF EXISTS stack"
   "DROP TABLE IF EXISTS pessoa"
   (slurp (io/resource "schema.sql"))])

(defn new-stack
  [pessoa coll]
  (mapv (fn [v] {:pessoa pessoa 
                 :ident v}) 
        coll))

(deftest hello
  (let [service-fn (setup-database migrations)]
    (is (= 0
           (-> service-fn
               (response-for :get "/contagem-pessoas")
               :body
               json/parse-string)))
    (is (= "/pessoas/josé"
           (-> service-fn
               (response-for :post "/pessoas"
                             :headers {"Content-Type" "application/json"}
                             :body (json/generate-string {:apelido    "josé"
                                                          :nome       "José Roberto"
                                                          :nascimento "2000-10-01"
                                                          :stack      (new-stack "josé" ["C#" "Node" "Oracle"])}))
               :headers
               (get "Location"))))
    (is (= "/pessoas/ana"
           (-> service-fn
               (response-for :post "/pessoas"
                             :headers {"Content-Type" "application/json"}
                             :body (json/generate-string {:apelido    "ana"
                                                          :nascimento "1985-09-23"
                                                          :nome       "Ana Barbosa"
                                                          :stack      (new-stack "ana" ["Node" "Postgres"])}))
               :headers
               (get "Location"))))
    (is (= {:apelido    "josé"
            :nascimento "2000-09-30"
            :nome       "José Roberto"
            :stack      ["C#" "Node" "Oracle"]}
           (-> service-fn
               (response-for :get "/pessoas/josé")
               :body
               (json/parse-string true))))
    (is (= [{:apelido    "josé",
             :nome       "José Roberto",
             :nascimento "2000-09-30",
             :stack      ["C#" "Node" "Oracle"]}
            {:apelido    "ana",
             :nome       "Ana Barbosa",
             :nascimento "1985-09-22",
             :stack      ["Node" "Postgres"]}]
           (-> service-fn
               (response-for :get "/pessoas?t=node")
               :body
               (json/parse-string true))))
    (is (= [{:apelido    "josé",
             :nome       "José Roberto",
             :nascimento "2000-09-30",
             :stack      ["C#" "Node" "Oracle"]}]
           (-> service-fn
               (response-for :get "/pessoas?t=berto")
               :body
               (json/parse-string true))))
    (is (= [] (-> service-fn (response-for :get "/pessoas?t=Python") :body (json/parse-string true))))
    (is (= 400 (-> service-fn (response-for :get "/pessoas") :status)))
    (is (= 2 (-> service-fn (response-for :get "/contagem-pessoas") :body json/parse-string)))))
