(ns jamesmacaulay.zelkova.signal-test
  #?(:cljs (:require [jamesmacaulay.async-tools.core :as tools]
                     [jamesmacaulay.zelkova.signal :as z]
                     [jamesmacaulay.zelkova.impl.signal :as impl]
                     [cljs.core.async :as async :refer [chan to-chan <! >!]]
                     [cljs.core.async.impl.protocols :as async-impl]
                     [cemerick.cljs.test :refer-macros (deftest is are testing)])
     :clj (:require [jamesmacaulay.async-tools.core :as tools]
                    [jamesmacaulay.zelkova.signal :as z]
                    [jamesmacaulay.zelkova.impl.signal :as impl]
                    [clojure.core.async :as async :refer [go go-loop chan to-chan <! >!]]
                    [clojure.core.async.impl.protocols :as async-impl]
                    [jamesmacaulay.async-tools.test :refer (deftest-async)]
                    [clojure.test :refer (deftest is are testing)]))
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                            [jamesmacaulay.async-tools.test :refer (deftest-async)])
     :clj (:import [java.util.Date])))

(defn event-constructor
  [topic]
  (partial impl/make-event topic))

(deftest test-signal-sources
  (let [input (z/input 0)
        foldp (z/foldp + 0 input)
        mapped (z/map vector input foldp)
        async (z/async mapped)]
    (are [sig sources] (= (impl/signal-deps sig) sources)
      input #{}
      foldp #{input}
      mapped #{input foldp}
      async #{mapped})))

(deftest test-memoized-graph-calculations
  (let [input (z/input 0 :some-topic)
        foldp (z/foldp + 0 input)
        mapped (z/map vector input foldp)
        async (z/async mapped)]
    (are [sig parents-map] (= (impl/parents-map sig) parents-map)
      input {input #{}}
      foldp {input #{}
             foldp #{input}}
      mapped {input #{}
              foldp #{input}
              mapped #{input foldp}}
      async {input #{}
             foldp #{input}
             mapped #{input foldp}
             async #{mapped}})
    (are [sig kids-map] (= (impl/kids-map sig) kids-map)
      input {input #{}}
      foldp {input #{foldp}
             foldp #{}}
      mapped {input #{foldp mapped}
              foldp #{mapped}
              mapped #{}}
      async {input #{foldp mapped}
             foldp #{mapped}
             mapped #{async}
             async #{}})
    (are [sig sorted-sigs] (= (impl/topsort sig) sorted-sigs)
      input [input]
      foldp [input foldp]
      mapped [input foldp mapped]
      async [input foldp mapped async])
    (are [sig kid-indexes-map] (= (impl/kid-indexes-map sig) kid-indexes-map)
      input {input #{}}
      foldp {input #{1}
             foldp #{}}
      mapped {input #{1 2}
              foldp #{2}
              mapped #{}}
      async {input #{1 2}
             foldp #{2}
             mapped #{3}
             async #{}})
    (are [sig inputs-by-topic] (= (impl/inputs-by-topic sig) inputs-by-topic)
      input {:some-topic [input]}
      foldp {:some-topic [input]}
      mapped {:some-topic [input]}
      async {:some-topic [input]
             (:relayed-event-topic async) [async]})))

(deftest-async t