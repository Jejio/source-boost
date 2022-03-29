(ns jamesmacaulay.zelkova.signal
  "This is Zelkova's core namespace."
  (:refer-clojure :exclude [map merge count reductions])
  #?(:clj (:require [clojure.core :as core]
                    [clojure.core.async :as async :refer [go go-loop <! >!]]
                    [clojure.core.async.impl.protocols :as async-impl]
                    [jamesmacaulay.zelkova.impl.signal :as impl])
     :cljs (:require [cljs.core :as core]
                     [cljs.core.async :as async :refer [<! >!]]
                     [cljs.core.async.impl.protocols :as async-impl]
                     [jamesmacaulay.zelkova.impl.signal :as impl]))
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go go-loop]])))

(defn input
  "Returns an input signal with initial value `init`. The signal propagates values
from events which match some `topic`. An asynchronous `value-source` may be provided,
which will be used as the default value source for the given event `topic`. `value-source`
may take the following forms:

* a function taking a live graph and an options map, and returns a channel of values
* a channel of values
* a mult of some such value channel"
  ([init] (input init (keyword (gensym))))
  ([init topic]
   (impl/make-signal {:init-fn             (constantly init)
                      :relayed-event-topic topic}))
  ([init topic value-source]
   (impl/make-signal {:init-fn             (constantly init)
                      :relayed-event-topic topic
                      :event-sources       {topic (impl/value-source->events-fn value-source topic)}})))

(defn write-port
  "Takes an `init` value and an optional `topic`, and returns an input signal
  which satisfies core.async's `WritePort` protocol. This allows you to put
  values onto the signal as if it were a channel. If the `write-port` is being
  used in multiple live graphs, each value put onto the `write-port` is
  sent to all graphs."
  ([init] (write-port init (keyword (gensym))))
  ([init topic]
    (let [write-port-channel (async/chan)]
      (impl/make-signal {:init-fn             (constantly init)
                         :relayed-event-topic topic
                         :event-sources       {topic (impl/value-source->events-fn write-port-channel topic)}
                         :write-port-channel  write-port-channel}))))

(defn- take-nothing
  [rf]
  (fn
    ([] (rf))
    ([result] (rf result))
    ([result _input] (ensure-reduced result))))

(defn constant
  "Returns a constant signal of the given value."
  [x]
  (impl/make-signal {:init-fn   (constantly x)
                     :sources   [:events]
                     :msg-xform take-nothing}))

(defn pipeline
  "Takes a stateless transducer `xform`, a fallback value `base`, and a signal
`sig`. Returns a new signal which pipes values from `sig` through `xform`.
Because transducers may filter out values, you must provide a `base` which will
be used as the derived signal's initial value if the initial value of `sig` ends
up being filtered. If multiple values are emitted from the transduction of the
initial value of `sig`, then the initial value of the new signal will be the
_last_ of those emitted. Stateful transducers will give unexpected results and
are not supported."
  [xform base sig]
  (let [parent-init-fn (:init-fn sig)
        init-fn (fn [live-graph opts]
                  (let [vals (sequence xform [(parent-init-fn live-graph opts)])]
                    (if (seq vals)
                      (last vals)
                      base)))
        msg-xform (comp (core/map (fn [[_event _prev [msg]]] msg))
                        (filter impl/fresh?)
                        (core/map impl/value)
                        xform
                        (core/map impl/fresh))]
    (impl/make-signal {:init-fn init-fn
                       :sources [sig]
                       :msg-xform msg-xform})))

(defn mapseq
  "Takes a mapping function `f` and a sequence of signal `sources`, and returns a
signal of values obtained by applying `f` to the values from the source signals."
  [f sources]
  (if (empty? sources)
    (constant (f))
    (let [sources (vec sources)
          msg-xform (comp (core/map (fn [[_event _prev msgs]] msgs))
                          (filter (fn [msgs] (some impl/fresh? msgs)))
                          (core/map (fn [msgs]
                                      (->> msgs
                                           (core/map impl/value)
                                           (apply f)
                                           (impl/fresh)))))]
      (impl/make-signal {:init-fn (fn [live-graph opts]
                                    (->> sources
                                         (core/map (fn [sig] ((:init-fn sig) live-graph opts)))
                                         (apply f)))
                         :sources sources
                         :msg-xform msg-xform}))))

(defn map
  "Takes a mapping function `f` and any number of signal `sources`, and returns a
signal of values obtained by applying `f` to the values from the source signals."
  [f & sources]
  (mapseq f sources))

(defn template
  "Takes a map whose values are signals, to be used as a template. Returns a new
signal whose values are maps of the same form as `signal-map`, but with the current
value of each signal in place of the signal itself."
  [signal-map]
  (let [ks (keys signal-map)]
    (mapseq (fn [& values]
              (zipmap ks values))
            (vals signal-map))))

(defn indexed-updates
  "Takes a map whose values are signals, to be used as a template. Returns a new
signal whose values are maps that include an entry for every signal in
`signal-map` with a fresh value. For example, assuming that `signal-map` is:

    {:a sig-a
     :b sig-b
     :c sig-c}

Then when `sig-a` has a fresh value of \"foo\", `sig-b`'s value is cached, and
`sig-c` has a fresh value of \"bar\", then the `indexed-updates` signal would
emit `{:a \"foo\" :c \"bar\"}. When none of the signals have fresh values, no
value is emitted from the `indexed-updates` signal. This means that this signal
never emits an empty map."
  [signal-map]
  (let [ks (keys signal-map)
        vs (vals signal-map)
        init-fn (fn [live-graph opts]
                  (zipmap ks (core/map (fn [s] ((:init-fn s) live-graph opts)) vs)))
        kv-xfor