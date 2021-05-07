(ns jamesmacaulay.zelkova.impl.signal
  "Implementation details for `jamesmacaulay.zelkova.signal`."
  #?(:cljs (:require [jamesmacaulay.async-tools.core :as tools]
                     [jamesmacaulay.zelkova.impl.time :as time]
                     [clojure.zip :as zip]
                     [clojure.set]
                     [alandipert.kahn :as kahn]
                     [cljs.core.async :as async :refer [<! >!]]
  