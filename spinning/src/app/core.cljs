(ns app.core
  (:require-macros
    [cljs.core.async.macros :refer [go go-loop]]
    [klang.macros :as macros])
  (:require
    ;[ankha.core :as ankha]
    [clairvoyant.core :as trace :include-macros true]
    [cljs.core.async :refer [<! >! chan close! pipe put! sliding-buffer take! timeout]]
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
          :count-frames? false
          :measure-fps? false
          :render? false
          :update-gol? false
          :update-soa? false
          }
    :ech {:env-keyboard-key nil
          :env-mouse-click nil
          :env-mouse-move nil
          }
    :env {:frame-count 0
          :frames-per-second 0
          :keyboard-key nil
          :mouse-move nil
          :time nil
          }
    :gol {:cells nil
          :generation 0
          }
    :soa {:channel nil
          :max-prime nil
          :prime-count 0
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

(defn on-env-animation-frame [timestamp]
  (swap! state update-in [:env :frame-count] inc)
  (get-in @state [:app :count-frames?]))

(defn on-env-frames-per-second [fps]
  (swap! state assoc-in [:env :frames-per-second] fps)
  (get-in @state [:app :measure-fps?]))

(declare start-gol! stop-gol!)

(defn on-env-keyboard-key [m]
  (console/info (:poly/keyword m))
  ;(inspect m)
  (condp = (:poly/keyword m)
    :a :>> #(start-gol!)
    :q :>> #(stop-gol!)
    nil))

(defn on-env-mouse-move [m]
  (swap! state assoc-in [:env :mouse-move] m))

(defn on-env-time-interval []
  (swap! state assoc-in [:env :time] (poly/js-now)))


;; -----------------------------------------------------------------------------
;; Event Subscriptions

(defn setup-event-subscriptions! []
  (swap! state assoc-in [:app :count-frames?] true)
  (poly/listen-animation-frame! on-env-animation-frame)
  (swap! state assoc-in [:app :measure-fps?] true)
  (poly/listen-fps-interval! on-env-frames-per-second)
  (poly/take-back! (get-event-channel :env-keyboard-key) on-env-keyboard-key)
  (poly/take-back! (get-event-channel :env-mouse-move) on-env-mouse-move))

(defn teardown-event-subscriptions! []
  (swap! state assoc-in [:app :count-frames?] false)
  (swap! state assoc-in [:env :frame-count] 0))
  (swap! state assoc-in [:app :measure-fps?] false)
  (swap! state assoc-in [:env :frames-per-second] 0)


;; -----------------------------------------------------------------------------
;; Timers

(defonce interval-for-env-time
  (js/setInterval on-env-time-interval 1000))  ; every second (1000 ms)


;; -----------------------------------------------------------------------------
;; Sieve of Eratosthenes Prime Number Generator

(defn chan-of-ints [xform start-n]
  (let [ints (chan 1 xform)]
    (go-loop [n start-n]
      (>! ints n)
      (recur (inc n)))
    ints))

(defn new-prime? [n knowm-primes]
  (every? #(not= 0 (mod n %)) knowm-primes))

(defn chan-of-primes []
  (let [primes (chan)]
    (go-loop [cur-xf (map identity)
              cur-ch (chan-of-ints cur-xf 2)
              knowns [2]]
      (let [prime  (<! cur-ch)
            knowns (conj knowns prime)
            new-xf (filter #(new-prime? % knowns))
            new-ch (chan-of-ints new-xf prime)]
        (>! primes prime)
        (recur new-xf new-ch knowns)))
    primes))

(defn update-soa! [primes]
  (swap! state update-in [:soa :prime-count] inc)
  (take! primes #(swap! state assoc-in [:soa :max-prime] %))
  (get-in @state [:app :update-soa?]))

(defn start-soa! []
  (console/info "start soa")
  (let [primes (chan-of-primes)]
    (swap! state assoc-in [:app :update-soa?] true)
    (swap! state assoc-in [:soa :channel] primes)
    (poly/listen-next-tick! (partial update-soa! primes))))

#_(defn start-soa! []
  (let [primes (chan-of-primes)]
    (go-loop []
      (when-let [next-prime (<! primes)]
        (swap! state update-in [:soa :prime-count] inc)
        (swap! state assoc-in [:soa :max-prime] next-prime)
        (recur)))))

(defn stop-soa! []
  (console/info "stop soa")
  (swap! state assoc-in [:app :update-soa?] false))


;; -----------------------------------------------------------------------------
;; Conway's Game of Life Cellular Automata

(def acorn #{[70 62] [71 60] [71 62] [73 61] [74 62] [75 62] [76 62]})

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

(defn create-cells [seed]
  (into {} (for [k seed] [k (create-cell)])))

(defn step [cells]
  (into {} (for [[k n] (->> (keys cells) (mapcat neighbors) (frequencies))
                 :let [cell (or (get cells k) (create-cell k cells))]
                 :when (or (= n 3) (and (= n 2) (contains? cells k)))]
             [k (update-in cell [:age] inc)])))

;; (defn step [cells]
;;   (let [cell-freq (->> (keys cells) (mapcat neighbors) (frequencies))
;;         work-chan (chan)
;;         new-cells {}]
;;     (go
;;      (for [[k n] cell-freq]
;;        :when (or (= n 3) (and (= n 2) (contains? cells k)))
;;        (>! work-chan [k n])))
;;     (go
;;      (into new-cells
;;            (when-let [[k n] (<! work-chan)]
;;              (let [cell (or (get cells k) (create-cell k cells))]
;;                [k (update-in cell [:age] inc)]))))
;;     new-cells))

;;   (go-loop []
;;     (when-let [taken (<! channel)]
;;       (callback taken)
;;       (recur))))

(defn setup-gol! []
  (swap! state assoc-in [:gol :cells] (create-cells acorn)))

(defn teardown-gol! []
  (swap! state assoc-in [:gol :cells] nil))

(defn update-gol! []
  (swap! state update-in [:gol :cells] step)
  (swap! state update-in [:gol :generation] inc)
  (get-in @state [:app :update-gol?]))

(defn start-gol! []
  (console/info "start gol")
  (swap! state assoc-in [:app :update-gol?] true)
  (poly/listen-next-tick! update-gol!))

(defn stop-gol! []
  (console/info "stop gol")
  (swap! state assoc-in [:app :update-gol?] false))


;; -----------------------------------------------------------------------------
;; Render Cycle

(defn render! [timestamp state]
  (let [app-name (get-in state [:app :name])
        env-time (get-in state [:env :time])
        f-count (str "F: " (get-in state [:env :frame-count]))
        fps (str "FPS: " (get-in state [:env :frames-per-second]))
        mouse-move (get-in state [:env :mouse-move])
        mouse-x (:client-x mouse-move)
        mouse-y (:client-y mouse-move)
        mouse-pos (str "M: [" mouse-x ":" mouse-y "]")
        prime-count (str "P: " (get-in state [:soa :prime-count]))
        max-prime (str "Max Prime: " (get-in state [:soa :max-prime]))
        ppf (str "PPF: " (/ (get-in state [:soa :prime-count]) (get-in state [:env :frame-count])))
        generation (str "Gen: " (get-in state [:gol :generation]))
        population (str "Pop: " (count (get-in state [:gol :cells])))
        display [f-count fps prime-count ppf generation population]]
    (set! (.-innerText (app-div)) (string/join ", " display))))

(defn render-cycle! [timestamp]
  (render! timestamp @state)
  (get-in @state [:app :rendering?]))

(defn start-rendering! []
  (console/info "start rendering")
  (swap! state assoc-in [:app :rendering?] true)
  (poly/listen-animation-frame! render-cycle!))

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
  (start-rendering!)
  (start-soa!)
  ;(start-gol!)
  )

(defn teardown []
  (console/info "teardown")
  (stop-gol!)
  (stop-soa!)
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
