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
                                  (z/to-chan))]
      (async/onto-chan numbers-input [1 2 3 4])
      (is (= [1 2 3 4] (<! (async/into [] output1))))
      (is (= [1 2 3 4] (<! (async/into [] output2))))
      (is (= [2 3 4 5] (<! (async/into [] incremented-output)))))))

(deftest-async test-io
  (go
    (let [number (event-constructor :numbers)
          in (z/input 0 :numbers)
          graph (z/spawn in)
          out (async/tap graph (chan))]
      (is (= 0 (impl/init graph)))
      (async/onto-chan graph (map number [1 2 3]))
      (is (= [1 2 3]
             (<! (async/into [] out)))))))

(deftest-async test-map
  (go
    (let [number (event-constructor :numbers)
          in (z/input 0 :numbers)
          incremented (z/map inc in)
          graph (z/spawn incremented)
          out (async/tap graph (chan))]
      (is (= 1 (impl/init graph)))
      (async/onto-chan graph (map number [1 2 3]))
      (is (= [2 3 4]
             (<! (async/into [] out)))))
    (let [[a b c] (map event-constructor [:a :b :c])
          ins (map (partial z/input 0) [:a :b :c])
          summed (apply z/map + ins)
          graph (z/spawn summed)
          out (async/tap graph (chan))]
      (is (= 0 (impl/init graph)))
      (async/onto-chan graph [(a 1) (b 2) (c 3) (a 10)])
      (is (= [1 3 6 15]
             (<! (async/into [] out)))))
    (let [zero-arity-+-map (z/spawn (z/map +))
          zero-arity-vector-map (z/spawn (z/map vector))]
      (is (= 0 (impl/init zero-arity-+-map)))
      (is (= [] (impl/init zero-arity-vector-map))))))

(deftest-async test-foldp
  (go
    (let [number (event-constructor :numbers)
          in (z/input 0 :numbers)
          sum (z/foldp + 0 in)
          graph (z/spawn sum)
          out (async/tap graph (chan))]
      (is (= 0 (impl/init graph)))
      (async/onto-chan graph (map number [1 2 3]))
      (is (= [1 3 6]
             (<! (async/into [] out)))))))

(deftest-async test-reductions-can-get-init-value-from-calling-function-with-zero-args
  (go
    (let [in (z/write-port 0)
          vectors (z/reductions conj in)
          out (z/to-chan vectors)]
      (async/onto-chan in [1 2 3])
      (is (= [[1] [1 2] [1 2 3]]
             (<! (async/into [] out)))))))

(deftest-async test-reductions-with-init-value
  (go
    (let [in (z/write-port nil)
          vectors (z/reductions conj {} in)
          out (z/to-chan vectors)]
      (async/onto-chan in [[:a 1] [:b 2] [:c 3]])
      (is (= [{:a 1} {:a 1 :b 2} {:a 1 :b 2 :c 3}]
             (<! (async/into [] out)))))))

(deftest-async test-regular-signals-are-synchronous
  (go
    (let [number (event-constructor :numbers)
          in (z/input 0 :numbers)
          decremented (z/map dec in)
          incremented (z/map inc in)
          combined (z/map (fn [a b] {:decremented a
                                            :incremented b})
                                 decremented
                                 incremented)
          graph (z/spawn combined)
          out (async/tap graph (chan))]
      (async/onto-chan graph (map number [2 10]))
      (is (= [{:decremented 1
               :incremented 3}
              {:decremented 9
               :incremented 11}]
             (<! (async/into [] out)))))))

(deftest-async test-constant
  (go
    (let [number (event-constructor :numbers)
          in (z/input 0 :numbers)
          foo (z/constant :foo)
          combined (z/map vector in foo)
          graph (z/spawn combined)
          out (async/tap graph (chan))]
      (is (= [0 :foo] (impl/init graph)))
      (async/onto-chan graph (map number [1 2 3]))
      (is (= [[1 :foo] [2 :foo] [3 :foo]]
             (<! (async/into [] out)))))))

(deftest-async test-merge
  (go
    (let [a (event-constructor :a)
          b (event-constructor :b)
          a-in (z/input 10 :a)
          b-in (z/input 20 :b)
          b-dec (z/map dec b-in)
          b-inc (z/map inc b-in)
          merged (z/merge a-in b-dec b-in b-inc)
          graph (z/spawn merged)
          out (async/tap graph (chan))]
      (is (= 10 (impl/init graph)))
      (async/onto-chan graph [(a 20) (b 30) (a 40) (b 50)])
      (is (= [20 29 40 49]
             (<! (async/into [] out)))))))

(deftest-async test-combine
  (go
    (let [number (event-constructor :numbers)
          in (z/input 0 :numbers)
          inc'd (z/map inc in)
          combined (z/combine [in inc'd])
          graph (z/spawn combined)
          out (async/tap graph (chan))]
      (is (= [0 1] (impl/init graph)))
      (async/onto-chan graph (map number [1 2]))
      (is (= [[1 2] [2 3]]
             (<! (async/into [] out)))))
    (let [empty-combined (z/spawn (z/combine []))]
      (is (= [] (impl/init empty-combined))))))


(deftest-async test-sample-on
  (go
    (let [pos (event-constructor :mouse-position)
          click ((event-constructor :mouse-clicks) :click)
          fake-mouse-position (z/input [0 0] :mouse-position)
          fake-mouse-clicks (z/input :click :mouse-clicks)
          sampled (z/sample-on fake-mouse-clicks fake-mouse-position)
          graph (z/spawn sampled)
          out (async/tap graph (chan))]
      (is (= [0 0] (impl/init graph)))
      (async/onto-chan graph
                       [(pos [10 10])
                        click
                        (pos [20 20])
                        (pos [30 30])
                        click
                        (pos [40 40])
                        (pos [50 50])
                        click])
      (is (= [[10 10] [30 30] [50 50]]
         