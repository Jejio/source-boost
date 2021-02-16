(defproject timelord "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.7.0-beta1"]
                 [org.clojure/clojurescript "0.0-3196"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [jamesmacaulay/zelkova "0.4.0"]
                 [figwheel "0.2.5-SNAPSHOT"]
                 [reagent "0.5.0-alpha3"]]

  :plugins [[lein-cljsbuild "1.0.4"]
            [lein-figwheel "0.2.5-SNAPSHOT"]]

  :source-paths ["src"]

  :clean-targets ^{:protect false} ["resources/public/js/compiled"]
  
  :cljsbuild {
    :builds [{:id "dev"
              :source-paths ["src" "dev_src"]
              :compiler {:output-to "resources/public/js/compiled/timelord.js"
                         :output-dir "resources/public/js/compiled/out"
                         :optimizations :none
                         :main timelord.dev
                         :asset-path "js/compiled/out"
                         :source-map true
                     