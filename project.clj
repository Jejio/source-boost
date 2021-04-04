(defproject jamesmacaulay/zelkova "0.4.0"
  :description "Elm-style FRP for Clojure and ClojureScript"
  :url "http://github.com/jamesmacaulay/zelkova"
  :license {:name "MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "0.0-3308"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]]
  :plugins [[lein-cljsbuild "1.0.5"]
            [com.cemerick/clojurescript.test "0.3.3"]
            [funcool/codeina "0.3.0-SNAPSHOT"]]

  :codeina {:sources ["src/jamesmacaulay/zelkova"]
            :exclude [jamesmacaulay.zelkova.impl.signal
                      jamesmacaulay.zelkova.impl.time]
            :reader :clojurescript
            :src-uri "http://github.com/jamesmacaulay/zelkova/blob/master/"
            :src-uri-prefix "#L"}

  :aliases {"repl" ["with-profile" "repl" "repl"]
            "cljs-test" ["cljsbuild" "test"]
            "cljs-autotest" ["cljsbuild" "auto" "test"]
            "all-tests" ["do" "clean" ["test"] ["cljs-test"]]}
  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.2.10"]
                     