(ns jamesmacaulay.async-tools.core-test
  #?(:cljs (:require [jamesmacaulay.async-tools.core :as tools]
                     [cljs.core.async :as async :refer [chan to-chan <! >!]]
                     [cljs.core.async.impl.protocols :as impl]
                     [cemerick.cljs.test :refer-macros (deftest is testing)])
     :clj (:require [jamesmacaulay.async-tools.core :as tools]
                    [clojure.core.async :as async :refer [go chan to-chan <! >!]]
                    [clojure.core.async.impl.protocols :as impl]
                    [jamesmacaulay.async