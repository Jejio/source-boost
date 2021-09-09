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
    :parents-map is a map of signals to their parents,
    :kids-map is a map of signals to their children."
  [signal]
  (loop [parents-map {}
         kids-map {signal #{}}
         loc (node-graph-zipper signal)]
    (cond
      (zip/end? loc)
      {:parents-map parents-map
       :kids-map kids-map}

      (contains? parents-map (zip/node loc))
      (recur parents-map kids-map (skip-subtree loc))

      :else
      (let [this-sig (zip/node loc)
            parents (signal-deps this-sig)
            next-sig (zip/next loc)]
        (recur
          (assoc parents-map this-sig parents)
          (merge-with clojure.set/union
                      kids-map
                      (zipmap parents (repeat #{this-sig})))
          next-sig)))))

(defn parents-map->topsort
  [pm]
  (->> pm (kahn/kahn-sort) (reverse) (into [])))

(defn topsort->topic-map
  [sorted-sigs]
  (reduce (fn [m sig]
            (if-let [topic (:relayed-event-topic sig)]
              (assoc m topic (conj (get m topic []) sig))
              m))
          {}
          sorted-sigs))

(defn build-kid-indexes-map
  [kids-map sorted-sigs]
  (let [signal->index (zipmap sorted-sigs (range))
        signals->sorted-index-set #(into (sorted-set) (map signal->index) %)]
    (zipmap (keys kids-map)
            (map signals->sorted-index-set (vals kids-map)))))

(defrecord SignalDefinitionMetadata
  [parents-map kids-map topsort kid-indexes-map inputs-by-topic])

(defn- attach-delayed-metadata
  [sig]
  (let [delayed-dep-maps (delay (calculate-dependency-maps sig))
        delayed-parents-map (delay (:parents-map @delayed-dep-maps))
        delayed-kids-map (delay (:kids-map @delayed-dep-maps))
        delayed-topsort (delay (parents-map->topsort @delayed-parents-map))
        delayed-topic-map (delay (topsort->topic-map @delayed-topsort))
        delayed-kid-indexes-map (delay (build-kid-indexes-map @delayed-kids-map @delayed-topsort))]
    (with-meta sig (->SignalDefinitionMetadata delayed-parents-map
                                               delayed-kids-map
                                               delayed-topsort
                                               delayed-kid-indexes-map
                                               delayed-topic-map))))

(defn- delegate-to-channel
  [f ch & args]
  (assert (not (nil? ch))
          "This signal is not a valid write-port, use the `jamesmacaulay.zelkova.signal/write-port` constructor if you want to treat this signal like a channel.")
  (apply f ch args))

(defrecord SignalDefinition
  [init-fn sources relayed-event-topic msg-xform deps event-sour