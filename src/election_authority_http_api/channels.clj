(ns election-authority-http-api.channels
  (:require [clojure.core.async :as async]))

(defonce ok-requests (async/chan))
(defonce ok-responses (async/chan))

(defonce election-authority-search (async/chan))

(defn close-all! []
  (doseq [c [ok-requests ok-responses election-authority-search]]
    (async/close! c)))
