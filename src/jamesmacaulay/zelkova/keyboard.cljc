(ns jamesmacaulay.zelkova.keyboard
  "This namespace provides keyboard-related signals."
  (:refer-clojure :exclude [meta])
  #?(:clj (:require [jamesmacaulay.zelkova.signal :as z]
                    [clojure.core.async :as async])
     :cljs (:require [jamesmacaulay.zelkova.signal :as z]
                     [goog.events :as events]
                     [cljs.core.async :as async :refer [>! <!]]))
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go go-loop]])))

#?(:cljs
   (defn- listen
     [el type & args]
     (let [out (apply async/chan 1 args)]
       (events/listen el type (fn [e] (async/put! out e)))
       out)))

(defn- keydown-channel
  [graph opts]
  #?(:cljs (listen js/document "keydown")
     :clj (async/chan)))

(defn- keyup-channel
  [graph opts]
  #?(:cljs (listen js/document "keyup")
     :clj (async/chan)))

(defn- blur-channel
  [graph opts]
  #?(:cljs (listen js/window "blur")
     :clj (async/chan)))

(def ^:private down-events
  (z/input 0 ::down-events keydown-channel))

(def ^:private up-events
  (z/input 0 ::up-events keyup-channel))

(def ^:private blur-events