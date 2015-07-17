(ns app.core
  (:require-macros
   [klang.macros :as macros])
  (:require
   ;[ankha.core :as ankha]
   [clairvoyant.core :as trace :include-macros true]
   [clojure.string :as string]
   [goog.dom :as dom]
   [goog.object]
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
;; App Div / Canvas

(defn app-div [] (poly/get-element :app))

(defn app-canvas [] (poly/get-element :app-canvas))


;; -----------------------------------------------------------------------------
;; State

(defonce state
  (atom
   {:app {:name "Spinning"
          :version "0.1.0"
          :measure-fps? false
          :rendering? false
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
    :gui {:cells nil
          :frame 0
          :generation 0
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

(declare start-rendering! stop-rendering!)

(defn on-env-keyboard-key [m]
  (console/info (:poly/keyword m))
  ;(inspect m)
  (condp = (:poly/keyword m)
    :a :>> #(start-rendering!)
    :q :>> #(stop-rendering!)
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

(defonce interval-for-env-time
  (js/setInterval on-env-time-interval 1000))  ; every second (1000 ms)


;; -----------------------------------------------------------------------------
;; Conway's Game of Life Cellular Automata

(def acorn #{[70 62] [71 60] [71 62] [73 61] [74 62] [75 62] [76 62]})

(defn setup-gol! []
  (swap! state assoc-in [:gui :cells] acorn))

(defn teardown-gol! []
  (swap! state assoc-in [:gui :cells] false))

(defn neighbors [[x y]]
  (map vector
       ((juxt inc      inc identity dec dec      dec identity inc) x)
       ((juxt identity inc inc      inc identity dec dec      dec) y)))

(defn create-cell
  ([]
   {:age 0 :color {:r (rand-int 256) :g (rand-int 256) :b (rand-int 256)}})
  ([k cells]
   ; Set newborn cell color using a blend of colors of its 3 living neighbors.
   (let [[c1 c2 c3] (map #(:color %1) (keep #(get cells %1) (neighbors k)))]
     {:age 0 :color {:r (:r c1) :g (:g c2) :b (:b c3)}})))

(defn step [cells]
  (into {} (for [[k n] (->> (keys cells) (mapcat neighbors) (frequencies))
                 :let [cell (or (get cells k) (create-cell k cells))]
                 :when (or (= n 3) (and (= n 2) (contains? cells k)))]
             [k (update-in cell [:age] inc)])))


;; -----------------------------------------------------------------------------
;; Update / Render Cycle

(defn update! []
  (swap! state update-in [:gui :generation] inc))

(defn render! [timestamp state]
  (let [app-name (get-in state [:app :name])
        env-time (get-in state [:env :time])
        fps (get-in state [:env :frames-per-second])
        mouse-move (get-in state [:env :mouse-move])
        mouse-x (:client-x mouse-move)
        mouse-y (:client-y mouse-move)
        mouse-pos (str "[" mouse-x ":" mouse-y "]")
        gen (get-in state [:gui :generation])
        display [gen fps env-time timestamp mouse-pos]]
    (set! (.-innerText (app-div)) (string/join ", " display))))

(defn on-env-animation-frame [timestamp]
  (update!)
  (render! timestamp @state)
  (get-in @state [:app :rendering?]))

(defn start-rendering! []
  (console/info "start rendering")
  (swap! state assoc-in [:app :rendering?] true)
  (poly/listen-animation-frame! on-env-animation-frame))

(defn stop-rendering! []
  (console/info "stop rendering")
  (swap! state assoc-in [:app :rendering?] false))


;; -----------------------------------------------------------------------------
;; Init / Load / Setup / Teardown

(defn setup []
  (console/info "setup")
  (poly/set-title! (get-in @state [:app :name]))
  (setup-event-channels!)
  (setup-event-subscriptions!)
  (setup-gol!)
  (start-rendering!))

(defn teardown []
  (console/info "teardown")
  (stop-rendering!)
  (teardown-gol!)
  (teardown-event-subscriptions!)
  (teardown-event-channels!))

(defn ^:export on-load []
  (console/info "on-load")
  (teardown)
  (setup))

(defn ^:export on-init []
  (console/info "on-init")
  (let [canvas (dom/createElement "canvas")]
    (goog.object/set canvas "id" "app-canvas")
    (goog.object/set canvas "width" 800)
    (goog.object/set canvas "height" 800)
    (dom/appendChild (poly/get-body) canvas))
  (setup)
  ;(inspect @state)
  )
