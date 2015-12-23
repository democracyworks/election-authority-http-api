(ns election-authority-http-api.service
  (:require [io.pedestal.http :as bootstrap]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.route.definition :refer [defroutes]]
            [io.pedestal.interceptor :refer [interceptor]]
            [ring.util.response :as ring-resp]
            [turbovote.resource-config :refer [config]]
            [pedestal-toolbox.params :refer :all]
            [pedestal-toolbox.content-negotiation :refer :all]
            [kehaar.core :as k]
            [clojure.core.async :refer [go alt! timeout]]
            [bifrost.core :as bifrost]
            [bifrost.interceptors :as bifrost.i]
            [election-authority-http-api.channels :as channels]
            [clojure.string :as str]
            [clojure.tools.logging :as log]))

(def ping
  (interceptor
   {:enter
    (fn [ctx]
      (assoc ctx :response (ring-resp/response "OK")))}))

(def request->ocd-id-search
  (bifrost.i/update-in-request [:query-params :ocd-division]
                               [:bifrost-params :ocd-divisions]
                               (partial conj [])))

(def collapse-response-to-first-result
  (bifrost.i/update-in-response [:body :authorities] [:body] first))

(def empty-response->404
  (interceptor
   {:leave
    (fn [{:keys [response] :as ctx}]
      (if (-> response :body :authorities empty?)
        (assoc-in ctx [:response :status] 404)
        ctx))}))

(def drop-authority-key
  (interceptor
   {:leave
    (fn [ctx]
      (if-let [authority (get-in ctx [:response :body :authority])]
        (assoc-in ctx [:response :body] authority)
        ctx))}))

(defroutes routes
  [[["/" {:post [:create-authority (bifrost/interceptor
                                    channels/election-authority-create)]}
     ^:interceptors [(body-params)
                     (negotiate-response-content-type ["application/edn"
                                                       "application/transit+json"
                                                       "application/transit+msgpack"
                                                       "application/json"
                                                       "text/plain"])
                     drop-authority-key]
     ["/ping" {:get [:ping ping]}]

     ["/search"
      {:get
       [:search-authorities (bifrost/interceptor
                             channels/election-authority-search)]}
      ^:interceptors [request->ocd-id-search
                      collapse-response-to-first-result]]

     ["/state/:state"
      {:get
       [:state-authority (bifrost/interceptor
                          channels/election-authority-search)]}
      ^:interceptors [(bifrost.i/update-in-request
                       [:path-params :state]
                       [:query-params :ocd-division]
                       #(->> %
                             str/lower-case
                             (str "ocd-division/country:us/state:")))
                      request->ocd-id-search
                      collapse-response-to-first-result
                      empty-response->404]]]]])

(defn service []
  {::env :prod
   ::bootstrap/router :linear-search
   ::bootstrap/routes routes
   ::bootstrap/resource-path "/public"
   ::bootstrap/allowed-origins (if (= :all (config [:server :allowed-origins]))
                                 (constantly true)
                                 (config [:server :allowed-origins]))
   ::bootstrap/host (config [:server :hostname])
   ::bootstrap/type :immutant
   ::bootstrap/port (config [:server :port])})
