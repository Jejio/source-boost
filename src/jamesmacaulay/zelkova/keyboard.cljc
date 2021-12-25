(ns jamesmacaulay.zelkova.keyboard
  "This namespace provides keyboard-related signals."
  (:refer-clojure :exclude [meta])
  #?(:clj (:require [jamesmacaulay.zelkova.signal :as z]
                    [clojure.core.async :as async])
     :cljs (:require [jamesmacaulay.zelkova.signal :as z]
                     [goog.events :as events]
                     [cljs.core.async :as async :refer [>! <!]]))
  #?(:cljs (:require-macros [cljs.core.