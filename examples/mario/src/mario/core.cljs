
(ns ^:figwheel-always mario.core
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [jamesmacaulay.zelkova.signal :as z]
            [jamesmacaulay.zelkova.time :as time]
            [jamesmacaulay.zelkova.keyboard :as keyboard]
            [jamesmacaulay.zelkova.window :as window]))

(enable-console-print!)

; Here is Elm's Mario example, translated to ClojureScript/Zelkova from this version:
; https://github.com/elm-lang/elm-lang.org/blob/009de952c89592c180c43b60137f338651a1f9f6/public/examples/Intermediate/Mario.elm

;import Keyboard
;import Window
;
;-- MODEL