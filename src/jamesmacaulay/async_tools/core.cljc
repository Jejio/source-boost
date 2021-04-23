(ns jamesmacaulay.async-tools.core
  (:refer-clojure :exclude [concat])
  #?(:cljs (:require [cljs.core.async :as async :refer [>! <! chan]]
                     [cljs.core.async.impl.protocols :as impl]
                     [cljs.core.as