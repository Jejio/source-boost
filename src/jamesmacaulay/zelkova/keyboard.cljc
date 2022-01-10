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
  (z/input 0 ::blur-events blur-channel))

(def ^:private empty-state {:alt-key false :meta-key false :key-codes #{}})

(defmulti ^:private event-action (fn [state event] (.-type event)))

(defmethod event-action "keydown"
  [state event]
  (-> state
      (update-in [:key-codes] conj (.-keyCode event))
      (assoc :alt (.-altKey event)
             :meta (.-metaKey event))))

(defmethod event-action "keyup"
  [state event]
  (-> state
      (update-in [:key-codes] disj (.-keyCode event))
      (assoc :alt (.-altKey event)
             :meta (.-metaKey event))))

(defmethod event-action "blur"
  [state event]
  empty-state)

(def ^:private key-merge
  (->> (z/merge down-events up-events blur-events)
       (z/reductions event-action empty-state)))

(defn- key-signal
  [f]
  (z/drop-repeats (z/map f key-merge)))

(def ^{:doc "A signal of sets of the numeric key codes of whichever keys are
currently depressed."}
  keys-down
  (key-signal :key-codes))

(defn directions
  "Takes a key code to associate with `up`, `down`, `left`, and `right`, and
returns a signal of maps with `:x` and `:y` keys, and values of -1, 0, or 1
based on which keys are pressed."
  [up down left right]
  (key-signal (fn [{:keys [key-codes]}]
                {:x (+ (if (key-codes right) 1 0)
                       (if (key-codes left) -1 0))
                 :y (+ (if (key-codes up) 1 0)
    