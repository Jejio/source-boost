(ns jamesmacaulay.async-tools.core-test
  #?(:cljs (:require [jamesmacaulay.async-tools.core :as tools]
                     [cljs.core.async :as async :refer [chan to-chan <! >!]]
                     [cljs.core.async.impl.protocols :as impl]
