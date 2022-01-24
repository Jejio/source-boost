
(ns jamesmacaulay.zelkova.mouse
  "This namespace provides mouse-related signals."
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [jamesmacaulay.zelkova.signal :as z]
            [goog.events :as events]
            [cljs.core.async :as async :refer [>! <!]]))

(defn- listen
  [el type & args]
  (let [out (apply async/chan 1 args)]
    (events/listen el type (fn [e] (async/put! out e)))
    out))

(defn- position-channel
  [graph opts]
  (listen js/document
          "mousemove"
          (map (fn [e] [(.-pageX (.-event_ e)) (.-pageY (.-event_ e))]))))

(def ^{:doc "A signal of mouse positions as `[x y]` vectors. Initial value is `[0 0]`."}
  position
  (z/input [0 0] ::position position-channel))
