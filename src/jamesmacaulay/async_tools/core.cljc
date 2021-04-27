(ns jamesmacaulay.async-tools.core
  (:refer-clojure :exclude [concat])
  #?(:cljs (:require [cljs.core.async :as async :refer [>! <! chan]]
                     [cljs.core.async.impl.protocols :as impl]
                     [cljs.core.async.impl.channels :as channels])
     :clj (:require [clojure.core.async :as async :refer [go go-loop >! <! chan]]
                     [clojure.core.async.impl.protocols :as impl]
                     [clojure.core.async.impl.channels :as channels]))
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go go-loop]])))

(defn concat