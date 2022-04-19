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
        kv-xform (comp (filter (fn [[k msg]] (impl/fresh? msg)))
                       (core/map (fn [[k msg]] [k (impl/value msg)])))
        msg-xform (comp (core/map (fn [[_event _prev msgs]]
                                    (into {} kv-xform (core/map vector ks msgs))))
                        (remove empty?)
                        (core/map impl/fresh))]
    (impl/make-signal {:init-fn   init-fn
                       :sources   vs
                       :msg-xform msg-xform})))

(defn foldp
  "Create a past-dependent signal (\"fold into the past\"). The values of a `foldp`
signal are obtained by calling `f` with two arguments: the current value of the
`source` signal, and the previous value of the new `foldp` signal (acting as the
\"accumulator\"). `init` provides the initial value of the new signal, and
therefore acts as the seed accumulator."
  [f base source]
  (impl/make-signal {:init-fn   (constantly base)
                     :sources   [source]
                     :msg-xform (comp (filter (fn [[_event _prev [msg]]]
                                                (impl/fresh? msg)))
                                      (core/map (fn [[_event prev [msg]]]
                                                  (impl/fresh (f (impl/value msg) prev)))))}))

(defn drop-repeats
  "Returns a signal which relays values of `sig`, but drops repeated equal values."
  [sig]
  (impl/make-signal {:init-fn   (:init-fn sig)
                     :sources   [sig]
                     :msg-xform (comp (filter (fn [[_event prev [msg]]]
                                                (and (impl/fresh? msg)
                                                     (not= prev (impl/value msg)))))
                                      (core/map (fn [[_event _prev [msg]]]
                                                  msg)))}))

(defn reductions
  "Create a past-dependent signal like `foldp`, with two differences:
* calls `f` with the arguments reversed to align with Clojure: the first
argument is the accumulator, the second is the current value of `source`.
* if `init` is omitted, the initial value of the new signal will be obtained by
calling `f` with no arguments."
  ([f source] (reductions f (f) source))
  ([f init source]
   (foldp (fn [val prev] (f prev val))
          init
          source)))

(defn select-step
  "Takes an initial value and a map whose keys are signals and whose values are
reducing functions. Returns a past-dependent signal like `reductions`, except
each signal has its own reducing function to use when that signal updates. If
more than one source signal updates from the same input event, then each
applicable reducing function is called to transform the state value in the
same order as they are defined in `signal-handlers-map`."
  [init & signals-and-handlers]
  (let [[signals handlers] (reduce (partial mapv conj)
                                   [[] []]
                                   (partition 2 signals-and-handlers))
        signal->handler (zipmap signals handlers)
        updates-signal (indexed-updates (zipmap signals signals))
        f (fn [prev updates-by-signal]
            (reduce (fn [acc [sig val]]
                      ((signal->handler sig) acc val))
                    prev
                    updates-by-signal))]
    (reductions f init updates-signal)))

(defn async
  "Returns an \"asynchronous\" version of `source`, splitting off a new subgraph which
does not maintain consistent event ordering relative to the main graph. In exchange,
signals which depend on an `async` signal don't have to wait for the `source` to finish
computing new values. This function is mainly useful in multithreaded environments when
you don't want a slow computation to block the whole graph."
  [source]
  (let [topic [::async source]
        msgs->events (comp cat
                           (filter impl/fresh?)
                           (core/map (fn [msg]
                                       (impl/make-event topic (impl/value msg)))))
        events-channel-fn (fn [live-graph _]
                            (async/tap (impl/signal-mult live-graph source)
                                       (async/chan 1 msgs->events)))]
    (impl/make-signal {:init-fn (:init-fn source)
                       :deps [source]
                       :relayed-event-topic topic
                       :event-sources {topic events-channel-fn}})))

(defn splice
  "Splice into the signal graph on the level of core.async channels. Takes a
`setup!` function which is called when the `source` signal gets wired up into
a live graph. The `setup!` function is passed two arguments: a `from` channel
and a `to` channel, in that order. The function is expected to be a consumer
of the `from` channel and a producer on the `to` channel, and should close the
`to` channel when the `from` channel is closed. There are no requirements for
how many values should be put on the `to` channel or when they should be sent.
`splice` returns a signal with an initial returned from `init-fn`. `init-fn`
takes two functions, a `live-graph` and an `opts` map. If no `init-fn` is
provided, then the initial value of `source` is used. The returned signal
asynchronously produces whichever values are put on the `to` channel in the
`setup!` function."
  ([setup! source]
    (splice setup! (:init-fn source) source))
  ([setup! init-fn source]
    (let [topic [::splice init-fn setup! source]
          events-channel-fn (fn [live-graph _]
                              (let [from (async/tap (impl/signal-mult live-graph source)
                                                    (async/chan 1 impl/fresh-values))
                                    to (async/chan 1 (core/map (partial impl/make-event topic)))]
                                (setup! from to)
                                to))]
      (impl/make-signal {:init-fn init-fn
                         :deps [source]
                         :relayed-event-topic topic
                         :event-sources {topic events-channel-fn}}))))

(defn mergeseq
  "Takes a sequence of signals `sigs`, and returns a new signal which relays fresh
values from all of the source signals. When more than one source 