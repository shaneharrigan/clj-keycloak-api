(ns mycljapp.mycljapp
  (:gen-class)
  (:require [buddy.auth.backends :as backends]
            [buddy.auth :refer [authenticated? throw-unauthorized]]
            [buddy.auth.middleware :as buddy-midd :refer [wrap-authentication]]
            [keycloak.deployment :as kc-deploy :refer [deployment client-conf]]
            [keycloak.backend :as kc-backend :refer [buddy-verify-token-fn]]
            [ring.util.response :as ring-response]
            [org.httpkit.server :as http-kit]
            [io.pedestal.interceptor :as interceptor]
            [io.pedestal.interceptor.chain :as interceptor.chain]
            [io.pedestal.interceptor.error :refer [error-dispatch]]
            [reitit.ring :as ring-router]
            [reitit.coercion.spec]
            [reitit.coercion.spec :as spec-coercion]
            [reitit.coercion.schema :as schema-coercion]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]])
  (:import [org.keycloak.exceptions TokenNotActiveException]))

(def keycloak-deployment (kc-deploy/deployment (kc-deploy/client-conf {:auth-server-url "http://localhost:8080"
                                                                       :realm            "{INSERT REALM}"
                                                                       :client-id        "{INSERT CLIENT ID}"
                                                                       :client-secret    "{INSERT CLIENT SECRET}"})))

;; Ring + reitit
(def backend (backends/token {:authfn (kc-backend/buddy-verify-token-fn keycloak-deployment) :token-name "Bearer"}))

(defn handler [request]
  (if (authenticated? request)
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body "Hello! World!"}
    {:status 401
     :headers {"Content-Type" "text/html"}
     :body "No access"}))

(defn exception-interceptor [context]
  (try
    (-> context :next)
    (catch Exception e
      (assoc context :response {:status 500
                                :headers {"Content-Type" "text/html"}
                                :body "Token is not active!"}))))

(def router
  (ring-router/router
   ["/api/hello"
    {:get (fn [request]
            {:status 401
             :headers {"Content-Type" "text/html"}
             :body "Hello! World!"})}]
   {:data {:interceptors [exception-interceptor]}}))


(def app
  (-> router
      (ring-router/ring-handler)
      (wrap-authentication backend)
      (wrap-defaults site-defaults)))

(defn start
  "Starts the Ring server."
  [& args]
  (let [port-arg (first args)
        port (if (string? port-arg)
               (Integer/parseInt port-arg)
               3033)]
    (http-kit/run-server app {:port port})
    (println (str "Server started on port " port))))