(ns jamesmacaulay.zelkova.impl.signal
  "Implementation details for `jamesmacaulay.zelkova.signal`."
  #?(:cljs (:require [jamesmacaulay.async-tools.core :as tools]
                     [jamesmacaulay.zelkova.impl.time :as time]
                     [clojure.zip :as zip]
                     [clojure.set]
                     [alandipert.kahn :as kahn]
                     [cljs.core.async :as async :refer [<! >!]]
                     [cljs.core.async.impl.protocols :as async-impl])

     :clj (:require [jamesmacaulay.async-tools.core :as tools]
                    [jamesmacaulay.zelkova.impl.time :as time]
                    [clojure.zip :as zip]
                    [clojure.set]
                    [alandipert.kahn :as kahn]
                    [clojure.core.async :as async :refer [go go-loop <! >!]]
                    [clojure.core.async.impl.protocols :as async-impl]))
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go go-loop]])))

(defprotocol BoxedValueProtocol
  (value [boxed]))

(defprotocol EventProtocol
  "Events come in from \"the outside world\" and get transformed into Messages by input signal nodes"
  (topic [event])
  (timestamp [event])
  (record-timestamp [event timestamp]))

(defprotocol MessageProtocol
  "Messages are propagated through the signal graph, and can either be \"fresh\" or \"cached\"."
  (fresh? [msg] "returns `true` if the message represents a fresh value, `false` otherwi