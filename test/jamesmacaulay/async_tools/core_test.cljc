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
  