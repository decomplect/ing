(ns app.core
  (:require-macros
   [klang.macros :as macros])
  (:require
   ;[ankha.core :as ankha]
   [clairvoyant.core :as trace :include-macros true]
   [clojure.string :as string]
   ;[ion.omni.core :as omni]
   [ion.poly.core :as poly]
   [klang.core :as klang]
   [shodan.console :as console :include-macros true]
   [shodan.inspection :refer [inspect]]
   ))

;(trace/trace-forms {:tracer trace/default-tracer})

(enable-console-print!)


;; -----------------------------------------------------------------------------
;; Logging

(defn init-logging! []
  (macros/add-form-meta! :line :file)
  (klang/init-single-mode!)
  (klang/init!)
  (klang/default-config!)
  (klang/logger ::INFO))

(defonce lg (init-logging!))


;; -----------------------------------------------------------------------------
;; App Div

(defonce app-div (poly/get-element :app))


;; -----------------------------------------------------------------------------
;; State

(defonce state
  (atom
   {:app {:name "Spinning"
          :version "0.1.0"
          }
    :ech {:env-keyboard-key nil
          :env-mouse-click nil
          :env-mouse-move nil
          }
    :env {:frames-per-second nil
          :keyboard-key nil
          :mouse-move nil
          :time nil
          }
    :gui {:click-count 0
          }}))


;; -----------------------------------------------------------------------------
;; Event Channels

(defn get-event-channel [k]
  (get-in @state [:ech k :channel]))

(defn get-event-listener-key [k]
  (get-in @state [:ech k :listener-key]))

(defn swap-event-channel! [k [listener-key channel]]
  (swap! state assoc-in [:ech k]
         {:channel channel :listener-key listener-key}))

(defonce setup-event-channels!
  (do
    (swap-event-channel! :env-keyboard-key (poly/listen-put! js/window :key))
    (swap-event-channel! :env-mouse-click (poly/listen-put! js/window :mouse-click))
    (swap-event-channel! :env-mouse-move (poly/listen-put! js/window :mouse-move))
    true))


;; -----------------------------------------------------------------------------
;; Event Handlers

(defn on-env-keyboard-key [m]
  (console/info (:ion.poly.core/keyword m))
  (inspect m)
  (condp = (:ion.poly.core/keyword m)
    :a :>> #(klang/hide!)
    :q :>> #(klang/show!)
    nil))

(defn on-env-mouse-move [m]
  (swap! state assoc-in [:env :mouse-move] m))

(defn on-env-time-interval []
  (swap! state assoc-in [:env :time] (poly/js-now)))

;; (defn on-gui-button-click [e]
;;   (mutate-gui-click-count!))


;; -----------------------------------------------------------------------------
;; Event Channel Processing

;; (defonce setup-event-take-backs!
;;   (poly/take-back! (second (poly/listen-put! js/window :key)) on-env-keyboard-key))

(defonce setup-event-take-backs!
  (do
    (poly/take-back! (get-event-channel :env-keyboard-key) on-env-keyboard-key)
    (poly/take-back! (get-event-channel :env-mouse-move) on-env-mouse-move)
  true))


;; -----------------------------------------------------------------------------
;; Timers

(defonce interval-for-env-time
  (js/setInterval on-env-time-interval 1000))  ; every second (1000 ms)


;; -----------------------------------------------------------------------------
;; Simple Animation Cycle

(defn enable-fps! []
  (swap! state assoc-in [:env :frames-per-second] (poly/measure-fps)))

(defn animate [timestamp state]
  (let [app-name (get-in state [:app :name])
        env-time (get-in state [:env :time])
        fps @(get-in state [:env :frames-per-second])
        mouse-move (get-in state [:env :mouse-move])
        mouse-x (:client-x mouse-move)
        mouse-y (:client-y mouse-move)
        mouse-pos (str "[" mouse-x ":" mouse-y "]")]
    (set! (.-innerText app-div)
          (string/join ", " [app-name fps env-time timestamp mouse-pos]))))

(declare request-simple-animation)

(defn simple-animation-cycle [timestamp]
  (request-simple-animation)
  (animate timestamp @state))

(def request-simple-animation (partial poly/request-animation-frame
                                       simple-animation-cycle))


;; -----------------------------------------------------------------------------
;; Simple Render Cycle

(defn render []
  ;Update the dom
  true)

(defn request-frame [f]
  (poly/request-animation-frame f))

(declare step)

(def request-step (partial request-frame step))

(defn step [timestamp]
  (request-step)
  (render))

(defn simple-render-cycle [timestamp]
  (poly/request-animation-frame simple-render-cycle)
  (render))


;; -----------------------------------------------------------------------------
;; Init/Load

(defn ^:export on-init []
  (console/info "on-init")
  (lg :on-init "Start intializing.")
  (poly/set-title! (get-in @state [:app :name]))
  ; Create a canvas element inside the "app" div.
  (lg :on-init "End intializing.")
  ;(klang/show!)
  ;(inspect @state)
  (enable-fps!)
  (request-simple-animation)
  ;(request-step)
  )

(defn ^:export on-load []
  ; stop
  (console/info "on-load")
  (lg :on-load "The Dom is in the house!")
  ; re-start
  )
