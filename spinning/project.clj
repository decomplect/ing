(defproject
  boot-project
  "0.1.0-SNAPSHOT"
  :dependencies
  [[adzerk/boot-cljs "0.0-3308-0" :scope "test"]
   [adzerk/boot-cljs-repl "0.1.10-SNAPSHOT" :scope "test"]
   [adzerk/boot-reload "0.3.1" :scope "test"]
   [pandeiro/boot-http "0.6.3-SNAPSHOT" :scope "test"]
   [org.clojure/clojure "1.7.0"]
   [org.clojure/clojurescript "0.0-3308"]
   [ankha "0.1.5-SNAPSHOT"]
   [ion/poly "0.1.0-SNAPSHOT"]
   [klang "0.2.0-SNAPSHOT"]
   [org.clojure/core.async "0.1.346.0-17112a-alpha"]
   [shodan "0.4.2"]
   [spellhouse/clairvoyant "0.1.0-SNAPSHOT"]]
  :source-paths
  ["src"])