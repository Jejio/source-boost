(ns jamesmacaulay.zelkova.window
  "This namespace provides window-related signals."
  (:refer-clojure :exclude [meta])
  #?(:clj (:require [jamesmacaulay.zelkova.signal :as z]
                    [jamesmacaulay.zelkova.impl.signal :as impl]
                    [clojure.core.async :as async])
     :cljs (:require [jamesmacaulay.zelkova.signal :as z]
                     [jamesmacaulay.zelkova.impl.signal :as impl]
                     [jamesmacaulay.async-tools.core :as tools]
                     [goog.events :as events]
                     [cljs.core.async :as async :refer [>! <!]]))
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go go-loop]])))

#?(:cljs
   (defn- listen
     [el type & args]
     (let [out (apply async/chan 1 args)]
       (events/listen el ty