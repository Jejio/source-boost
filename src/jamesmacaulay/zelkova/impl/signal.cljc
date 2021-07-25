(ns jamesmacaulay.zelkova.impl.signal
  "Implementation details for `jamesmacaulay.zelkova.signal`."
  #?(:cljs (:require [jamesmacaulay.async-tools.core :as tools]
                     [jamesmacaulay.zelkova.impl.time :as time]
                     [clojure.zip :as zip]
                     [clojure.set]
                     [alandipert.kahn :as kahn]
                     [cljs.core.async :as async :refer [<! >!]]
                     [cljs.core.async.impl.protocols :as async-impl])

     :clj (:require [jamesmacaulay.async-tools.core :as tools]
                    [jamesmacaulay.zelkova.impl.time :as time]
                    [clojure.zip :as zip]
                    [clojure.set]
                    [alandipert.kahn :as kahn]
                    [clojure.core.async :as async :refer [go go-loop <! >!]]
                    [clojure.core.async.impl.protocols :as async-impl]))
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go go-loop]])))

(defprotocol BoxedValueProtocol
  (value [boxed]))

(defprotocol EventProtocol
  "Events come in from \"the outside world\" and get transformed into Messages by input signal nodes"
  (topic [event])
  (timestamp [event])
  (record-timestamp [event timestamp]))

(defprotocol MessageProtocol
  "Messages are propagated through the signal graph, and can either be \"fresh\" or \"cached\"."
  (fresh? [msg] "returns `true` if the message represents a fresh value, `false` otherwise"))

; an external event
(defrecord Event
  [topic value timestamp]
  BoxedValueProtocol
  (value [_] value)
  EventProtocol
  (topic [_] topic)
  (timestamp [_] timestamp)
  (record-timestamp [e t] (assoc e :timestamp t)))

(defn make-event
  [topic value]
  (->Event topic value nil))

; a message representing a "fresh" signal value
(defrecord Fresh
  [value]
  BoxedValueProtocol
  (value [_] value)
  MessageProtocol
  (fresh? [_] true))

; a message representing a "cached" signal value
(defrecord Cached
  [value]
  BoxedValueProtocol
  (value [_] value)
  MessageProtocol
  (fresh? [_] false))

(defn fresh
  [value]
  (->Fresh value))

(defn cached
  [value]
  (->Cached value))

(def ^{:doc "A transducer which takes in batches of signal graph messages and pipes out fresh values."}
  fresh-values
  (comp cat
        (filter fresh?)
        (map value)))

; compiling graphs:

(defprotocol SignalProtocol
  (input? [s])
  (signal-deps [s] "returns the set of \"parent\" signals on which this signal depends")
  (parents-map [s])
  (kids-map [s])
  (topsort [s])
  (inputs-by-topic [s])
  (kid-indexes-map [s]))

(defn signal?
  "returns `true` if the argument satisfies `SignalProtocol`, `false` otherwise"
  [x]
  (satisfies? SignalProtocol x))

(defn- node-graph-zipper
  "Takes a signal and returns a zipper which can be used to traverse the signal graph."
  [output-node]
  (zip/zipper (constantly true)
              (comp seq signal-deps)
              nil
              output-node))

(defn- skip-subtree
  "Returns a new zipper location that skips the whole subtree at `loc`."
  [loc]
  (or (zip/right loc)
      (loop [p loc]
        (if (zip/up p)
          (or (zip/right (zip/up p))
              (recur (zip/up p)))
          [(zip/node p) :end]))))

(defn calculate-dependency-maps
  "Takes a signal and returns a map of two maps:
    :parents-map i