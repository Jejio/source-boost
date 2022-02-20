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
from events which match some `topic`. An asynchronous `