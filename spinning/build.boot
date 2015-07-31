(set-env!
  :source-paths   #{"src"}
  :resource-paths #{"../html"}
  :dependencies '[
    ; For boot
    [adzerk/boot-cljs "0.0-3308-0" :scope "test"]
    [adzerk/boot-cljs-repl "0.1.10-SNAPSHOT" :scope "test"]
    [adzerk/boot-reload "0.3.1" :scope "test"]
    [pandeiro/boot-http "0.6.3-SNAPSHOT" :scope "test"]
    [org.clojure/clojure "1.7.0"]
    [org.clojure/clojurescript "0.0-3308"]
    ; Libraries
    [ankha "0.1.5-SNAPSHOT"]
    [ion/poly "0.1.0-SNAPSHOT"]
    [klang "0.2.0-SNAPSHOT"]
    [org.clojure/core.async "0.1.346.0-17112a-alpha"]
    [shodan "0.4.2"]
    [spellhouse/clairvoyant "0.1.0-SNAPSHOT"]
  ])

(require
  '[adzerk.boot-cljs :refer [cljs]]
  '[adzerk.boot-cljs-repl :refer [cljs-repl start-repl]]
  '[adzerk.boot-reload :refer [reload]]
  '[pandeiro.boot-http :refer [serve]])

(task-options!
  reload {:on-jsload 'app.core/on-load}
  serve {:dir "target/"})

(deftask dev
  "Set env and task options for development use."
  []
  (set-env! :source-paths #{"src" "../../ion/src"})
  (task-options!
    cljs {:optimizations :none
          :source-map true}))

(deftask pro
  "Set env and task options for production use."
  []
  (set-env! :source-paths #{"src"})
  (task-options!
    cljs {:optimizations :advanced
          :source-map true}))

(deftask deploy
  []
  (comp (sift :exclude #"out/*") (cljs)))

(deftask run
  "Serve cljs application in browser with reload and repl."
  []
  (comp (serve) (watch) (speak) (reload) #_(cljs-repl) (cljs)))
