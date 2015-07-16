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

(defn app-div [] (poly/get-element :app))


;; -----------------------------------------------------------------------------
;; State

(defonce state
  (atom
   {:app {:name "Spinning"
          :version "0.1.0"
          :measure-fps? false
          :simple-animation? false
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

(defn setup-event-channel! [k [listener-key channel]]
  (swap! state assoc-in [:ech k]
         {:channel channel :listener-key listener-key}))

(defn setup-event-channels! []
  (setup-event-channel! :env-keyboard-key (poly/listen-put! js/window :key))
  (setup-event-channel! :env-mouse-click (poly/listen-put! js/window :mouse-click))
  (setup-event-channel! :env-mouse-move (poly/listen-put! js/window :mouse-move)))

(defn teardown-event-channel! [k]
  (poly/unlisten! (get-event-listener-key k) (get-event-channel k))
  (swap! state assoc-in [:ech k] nil))

(defn teardown-event-channels! []
  (map teardown-event-channel! (keys (:ech @state))))


;; -----------------------------------------------------------------------------
;; Event Handlers

(defn on-env-frames-per-second [fps]
  (swap! state assoc-in [:env :frames-per-second] fps)
  (get-in @state [:app :measure-fps?]))

(defn on-env-keyboard-key [m]
  (console/info (:poly/keyword m))
  ;(inspect m)
  (condp = (:poly/keyword m)
    :a :>> #(klang/hide!)
    :q :>> #(klang/show!)
    nil))

(defn on-env-mouse-move [m]
  (swap! state assoc-in [:env :mouse-move] m))

(defn on-env-time-interval []
  (swap! state assoc-in [:env :time] (poly/js-now)))


;; -----------------------------------------------------------------------------
;; Event Subscriptions

(defn setup-event-subscriptions! []
  (swap! state assoc-in [:app :measure-fps?] true)
  (poly/listen-fps! on-env-frames-per-second)
  (poly/take-back! (get-event-channel :env-keyboard-key) on-env-keyboard-key)
  (poly/take-back! (get-event-channel :env-mouse-move) on-env-mouse-move))

(defn teardown-event-subscriptions! []
  (swap! state assoc-in [:app :measure-fps?] false))


;; -----------------------------------------------------------------------------
;; Timers

;; (defonce interval-for-env-time
;;   (js/setInterval on-env-time-interval 1000))  ; every second (1000 ms)


;; -----------------------------------------------------------------------------
;; Simple Animation Cycle

(defn animate [timestamp state]
  (let [app-name (get-in state [:app :name])
        env-time (get-in state [:env :time])
        fps (get-in state [:env :frames-per-second])
        mouse-move (get-in state [:env :mouse-move])
        mouse-x (:client-x mouse-move)
        mouse-y (:client-y mouse-move)
        mouse-pos (str "[" mouse-x ":" mouse-y "]")]
    (set! (.-innerText (app-div))
          (string/join ", " [app-name fps env-time timestamp mouse-pos]))))

(declare request-simple-animation)

(defn simple-animation-cycle [timestamp]
  (when (get-in @state [:app :simple-animation?])
    (request-simple-animation)
    (animate timestamp @state)))

(def request-simple-animation (partial poly/request-animation-frame
                                       simple-animation-cycle))

(defn setup-simple-animation! []
  (swap! state assoc-in [:app :simple-animation?] true)
  (request-simple-animation))

(defn teardown-simple-animation! []
  (swap! state assoc-in [:app :simple-animation?] false))


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
;; Init/Load/Setup/Teardown

(defn setup []
  (console/info "setup")
  (poly/set-title! (get-in @state [:app :name]))
  (setup-event-channels!)
  (setup-event-subscriptions!)
  (setup-simple-animation!))

(defn teardown []
  (console/info "teardown")
  (teardown-simple-animation!)
  (teardown-event-subscriptions!)
  (teardown-event-channels!))

(defn ^:export on-load []
  (console/info "on-load")
  (teardown)
  (setup))

(defn ^:export on-init []
  (console/info "on-init")
  ; Create a canvas element inside the "app" div.
  (setup)
  ;(inspect @state)
  )
