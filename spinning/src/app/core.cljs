(ns app.core
  (:require-macros
   [klang.macros :as macros])
  (:require
   [ankha.core :as ankha]
   [clairvoyant.core :as trace :include-macros true]
   [ion.omni.core :as omni]
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
  ;(macros/add-form-meta! :line :file)
  (klang/init-single-mode!)
  (klang/init!)
  (klang/default-config!)
  (klang/logger ::INFO))

(defonce lg (init-logging!))


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
    :env {:keyboard-key nil
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
    (lg "Setting up event channels")
    (swap-event-channel! :env-keyboard-key (poly/listen-put! js/window :key))
    (swap-event-channel! :env-mouse-click (poly/listen-put! js/window :mouse-click))
    (swap-event-channel! :env-mouse-move (poly/listen-put! js/window :mouse-move))
    true))


;; -----------------------------------------------------------------------------
;; State Mutators

;; (defn mutate-env-keyboard-key! [m]
;;   (reset! rc-env-keyboard-key m))

;; (defn mutate-env-mouse-move! [m]
;;   (reset! rc-env-mouse-move m))

;; (defn mutate-gui-click-count! []
;;   (reset! rc-gui-click-count))


;; -----------------------------------------------------------------------------
;; Event Handlers

(defn on-env-keyboard-key [m]
  (console/info m)
  (lg :on-env-keyboard-key m)
  (condp = (:keyword m)
    :q :>> #(klang/show!)
    nil))

(defn on-env-mouse-move [m]
  #_(lg :on-env-mouse-move m))

;; (defn on-env-keyboard-key [m]
;;   (mutate-env-keyboard-key! m))

;; (defn on-env-mouse-move [m]
;;   (mutate-env-mouse-move! m))

;; (defn on-env-time-interval []
;;   (mutate-env-time!))

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

;; (defonce interval-for-env-time
;;   (js/setInterval on-env-time-interval 1000))  ; every second (1000 ms)


;; -----------------------------------------------------------------------------
;; Render / Spin

(defn render [state]
  ;Update the dom
  true)

(defn spin []
  (omni/spin :on-render render
             :state state))


;; -----------------------------------------------------------------------------
;; Init/Load

(defn ^:export on-init []
  (console/info "on-init")
  (lg :on-init "Start intializing.")
  (poly/set-title! (get-in @state [:app :name]))
  ; Create a canvas element inside the "app" div.
  (lg :on-init "End intializing.")
  ;(klang/show!)
  (inspect @state)
  (spin))

(defn ^:export on-load []
  ; stop
  (console/info "on-load")
  (lg :on-load "Dom is in the house!")
  ; re-start
  )
