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

(deftest-async test-to-chan
  (go
    (let [in (z/write-port 0)
          incrd (z/map inc in)
          raw-out (z/to-chan incrd)
          filtered-out (z/to-chan incrd 1 (filter odd?))]
      (async/onto-chan in [1 2 3 4])
      (is (= [2 3 4 5] (<! (async/into [] raw-out))))
      (is (= [3 5] (<! (async/into [] filtered-out)))))))


(deftest-async test-wiring-things-up
  (go
    (let [number (event-constructor :numbers)
          letter (event-constructor :letters)
          numbers-input (z/input 0 :numbers)
          letters-input (z/input :a :letters)
          pairs (z/map vector numbers-input letters-input)
          live-graph (z/spawn pairs)
          output (async/tap live-graph (chan))]
      (async/onto-chan live-graph
                       [(number 1)
                        (letter :b)
                        (number 2)
                        (letter :c)])
      (is (= [[1 :a] [1 :b] [2 :b] [2 :c]]
             (<! (async/into [] output)))))))

(deftest-async test-write-port-broadcasts-to-all-dependent-live-graphs
  (go
    (let [numbers-input (z/write-port 0)
          output1 (z/to-chan numbers-input)
          output2 (z/to-chan numbers-input)
          incremented-output (->> numbers-input
                                  (z/map inc)
                                  (z/to-cha