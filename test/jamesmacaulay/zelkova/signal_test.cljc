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
                    [clojure.core.async.i