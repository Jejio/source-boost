(ns ^:figwheel-always timelord.core
  (:require [reagent.core :as reagent :refer [atom]]
            [jamesmacaulay.zelkova.signal :as z]
            [jamesmacaulay.zelkova.impl.signal 