(ns jamesmacaulay.async-tools.core-test
  #?(:cljs (:require [jamesmacaulay.async-tools.core :as tools]
                     [cljs.core.async :as async :refer [chan to-chan <! >!]]
                     [cljs.core.async.impl.protocols :as impl]
                     [cemerick.cljs.test :refer-macros (deftest is testing)])
     :clj (:require [jamesmacaulay.async-tools.core :as tools]
                    [clojure.core.async :as async :refer [go chan to-chan <! >!]]
                    [clojure.core.async.impl.protocols :as impl]
                    [jamesmacaulay.async-tools.test :refer (deftest-async)]
                    [clojure.test :refer (deftest is testing)]))
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go]]
                            [jamesmacaulay.async-tools.test :refer (deftest-async)])))

(deftest-async test-concat
  (go
    (is (= [1 2 3 4 5 6]
           (->> [[1 2] [3] [4 5] [6]]
                (map async/to-chan)
                (apply tools/concat)
                (async/into [])
                <!)))))

(deftest-async test-do-effects
  (go
    (let [box (atom 0)
          in (chan)
          out (chan)
          ret (tools/do-effects (partial async/put! out) in)]
      (is (= in ret))
   