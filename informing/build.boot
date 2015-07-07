(set-env!
  :source-paths   #{"src"}
  :resource-paths #{"../html"}
  :dependencies '[
    [adzerk/boot-cljs      "0.0-3308-0"      :scope "test"]
    [adzerk/boot-cljs-repl "0.1.10-SNAPSHOT" :scope "test"]
    [adzerk/boot-reload    "0.3.1"           :scope "test"]
    [pandeiro/boot-http    "0.6.3-SNAPSHOT"  :scope "test"]
    [org.clojure/clojure "1.7.0"]
    [org.clojure/clojurescript "0.0-3308"]
    [org.clojure/core.async "0.1.346.0-17112a-alpha"]
    [freactive "0.2.0-SNAPSHOT"]
    [garden "1.2.5"]
    [ion/cuss "0.1.0-SNAPSHOT"]
    [ion/poly "0.1.0-SNAPSHOT"]
  ])

(require
  '[adzerk.boot-cljs :refer [cljs]]
  '[adzerk.boot-cljs-repl :refer [cljs-repl start-repl]]
  '[adzerk.boot-reload :refer [reload]]
  '[pandeiro.boot-http :refer [serve]])

(task-options!
  reload {:on-jsload 'app.core/init}
  serve {:dir "target/"})

(deftask build []
  (task-options! cljs {:compiler-options {:closure-defines {:goog.dom.ASSUME_STANDARDS_MODE true}}})
  (set-env! :source-paths #{"src"})
  (comp (cljs :optimizations :advanced)))

(deftask dev []
  (task-options!
    checkout {:dependencies '[[ion/cuss "0.1.0-SNAPSHOT"]
                              [ion/poly "0.1.0-SNAPSHOT"]]}
    cljs {:optimizations :none
          :source-map true})
  (comp (serve) (watch) #_(checkout) (speak) (reload) (cljs-repl) (cljs)))
