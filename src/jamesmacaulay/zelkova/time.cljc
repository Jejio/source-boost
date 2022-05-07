
(ns jamesmacaulay.zelkova.time
  "Functions for working with time."
  (:refer-clojure :exclude [second delay])
  #?(:cljs (:require [jamesmacaulay.zelkova.impl.time :as t]
                     [jamesmacaulay.zelkova.signal :as z]
                     [jamesmacaulay.zelkova.impl.signal :as impl]
                     [cljs.core.async :as async :refer [>! <!]])

    :clj (:require [jamesmacaulay.zelkova.impl.time :as t]
                   [jamesmacaulay.zelkova.signal :as z]
                   [jamesmacaulay.zelkova.impl.signal :as impl]
                   [clojure.core.async :as async :refer [>! <! go go-loop]]))
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go go-loop]])))

(def millisecond 1)
(def second 1000)
(def minute (* 60 second))
(def hour (* 60 minute))

(defn in-milliseconds [ms] ms)
(defn in-seconds [ms] (/ ms second))
(defn in-minutes [ms] (/ ms minute))
(defn in-hours [ms] (/ ms hour))

(defn- fps-channel-fn
  [n]
  (fn [graph opts]
    (let [ms-per-frame (/ 1000 n)
          out (async/chan)]
      (go-loop [t (t/now)
                error 0]
        (<! (async/timeout (- ms-per-frame error)))
        (let [new-t (t/now)
              diff (- new-t t)]
          (>! out diff)