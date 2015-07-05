(set-env!
  :source-paths   #{"src"}
  :resource-paths #{"../html"}
  :dependencies '[
    [adzerk/boot-cljs           "0.0-3308-0"      :scope "test"]
    [adzerk/boot-cljs-repl      "0.1.10-SNAPSHOT" :scope "test"]
    [adzerk/boot-reload         "0.3.1"           :scope "test"]
    [boot-cljs-test/node-runner "0.1.0"           :scope "test"]
    [pandeiro/boot-http         "0.6.3-SNAPSHOT"  :scope "test"]
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
  '[boot-cljs-test.node-runner :refer [cljs-test-node-runner run-cljs-test]]
  '[pandeiro.boot-http :refer [serve]])

(task-options!
  reload {:on-jsload 'app.core/init}
  serve {:dir "target/"})

(deftask build []
  (task-options! cljs {:compiler-options {:closure-defines {:goog.dom.ASSUME_STANDARDS_MODE true}}})
  (set-env! :source-paths #{"src"})
  (comp (cljs :optimizations :advanced)))

(deftask dev []
  (comp (serve)
        (watch)
        (speak)
        (reload)
        (cljs-repl)
        (cljs :source-map true :optimizations :none)))

(deftask tst []
  (set-env! :source-paths #{"src" "test"})
  (comp (serve)
        (watch)
        (speak)
        (reload)
        (cljs-repl)
        (cljs-test-node-runner :namespaces '[app.test])
        (cljs :source-map true :optimizations :none)
        (run-cljs-test)))
