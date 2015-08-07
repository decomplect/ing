(ns app.core
  (:require-macros
    [cljs.core.async.macros :refer [go go-loop]]
    [klang.macros :as macros])
  (:require
    ;[ankha.core :as ankha]
    [clairvoyant.core :as trace :include-macros true]
    [cljs.core.async :as async :refer [<! >! chan close! onto-chan pipe put! take! timeout]]
    [clojure.string :as string]
    [goog.async.nextTick]
    [goog.dom :as dom]
    [goog.object]
    ;[ion.omni.core :as omni]
    [ion.poly.core :as poly]
    [klang.core :as klang]
    [shodan.console :as console :include-macros true]
    [shodan.inspection :refer [inspect]]))

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

(defn app-context [] (-> (app-canvas) (.getContext "2d")))


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
          :update-soe? false
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
    :gol {:busy? false
          :cells nil
          :generation 0
          }
    :soe {:channel nil
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
(declare start-soe! stop-soe!)

(defn on-env-keyboard-key [m]
  (console/info (:poly/keyword m))
  ;(inspect m)
  (condp = (:poly/keyword m)
    :a :>> #(start-gol!)
    :q :>> #(stop-gol!)
    :s :>> #(start-soe!)
    :w :>> #(stop-soe!)
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

(defn posmod-sift []
  (fn [rf]
    (let [seen (volatile! [])]
      (fn
        ([] (rf))
        ([result] (rf result))
        ([result input]
         (if (every? #(pos? (mod input %)) @seen)
           (do (vswap! seen conj input)
               (rf result input))
           result))))))

(defn chan-of-primes []
  (let [inputs (filter odd? (drop 3 (range)))
        primes (chan 1 (posmod-sift))]
    (put! primes 2)
    (onto-chan primes inputs)
    primes))

(defn setup-soe! []
  (swap! state assoc-in [:soe :channel] (chan-of-primes)))

(defn teardown-soe! []
  (swap! state assoc-in [:soe :channel] nil)
  (swap! state assoc-in [:soe :max-prime] nil)
  (swap! state assoc-in [:soe :prime-count] 0))

(defn update-soe! [primes-ch]
  (swap! state update-in [:soe :prime-count] inc)
  (take! primes-ch #(swap! state assoc-in [:soe :max-prime] %))
  (get-in @state [:app :update-soe?]))

(defn start-soe! []
  (console/info "start soe")
  (swap! state assoc-in [:app :update-soe?] true)
  (poly/listen-next-tick! (partial update-soe! (get-in @state [:soe :channel]))))

#_(defn start-soe! []
  (console/info "start soe")
  (swap! state assoc-in [:app :update-soe?] true)
  (let [primes-ch (get-in @state [:soe :channel])]
    (go-loop []
     (when-let [continue? (get-in @state [:app :update-soe?])]
       (when-let [next-prime (<! primes-ch)]
         (swap! state assoc-in [:soe :max-prime] next-prime)
         (swap! state update-in [:soe :prime-count] inc)
         (<! (timeout 0))
         (recur))))))

(defn stop-soe! []
  (console/info "stop soe")
  (swap! state assoc-in [:app :update-soe?] false))


;; -----------------------------------------------------------------------------
;; Conway's Game of Life Cellular Automata

(def acorn #{[70 62] [71 60] [71 62] [73 61] [74 62] [75 62] [76 62]})

(defn neighbors [[x y]]
  (map vector
       ((juxt inc      inc identity dec dec      dec identity inc) x)
       ((juxt identity inc inc      inc identity dec dec      dec) y)))

(defn living-neighbors [cells k]
  (keep #(get cells %1) (neighbors k)))

(defn create-cell
  ([]
   (create-cell {:r (rand-int 256) :g (rand-int 256) :b (rand-int 256)}))
  ([color]
   {:age 0 :color color}))

(defn create-cells [seed]
  (into {} (for [k seed] [k (create-cell)])))

(defn color-blend [c1 c2 c3]
  {:r (:r c1) :g (:g c2) :b (:b c3)})

(defn newborn [cells k]
  "Return a newborn cell with a blend of colors of its 3 living neighbors."
  (create-cell (apply color-blend (map :color (living-neighbors cells k)))))

(defn cell-fate [cells [k n]]
  "Determine if a cell will be part of the next generation."
  (when (or (= n 3) (and (= n 2) (contains? cells k)))
    (let [cell (or (get cells k) (newborn cells k))
          cell (update-in cell [:age] inc)]
      [k cell])))

(defn yield []
  (let [ch (chan)]
    (goog.async.nextTick #(close! ch))
    ch))

(defn step-chan [cells]
  "Returns a channel containing a single map of the next generation of cells."
  (go
    (console/time-start "neighborhood")
    (let [generation (chan 1 (keep (partial cell-fate cells)))
          neighborhood (mapcat neighbors (keys cells))]
      (console/time-end "neighborhood")
      (console/time-start "cell-neighbor-freq")
      (let [cell-neighbor-freq (frequencies neighborhood)]
        (console/time-end "cell-neighbor-freq")
        ;(<! (timeout 0))
        (<! (yield))
        (console/time-start "onto-chan generation")
        (onto-chan generation cell-neighbor-freq)
        (console/time-end "onto-chan generation")
        ;(<! (timeout 0))
        (<! (yield))
        (<! (async/into {} generation))))))

(defn setup-gol! []
  (swap! state assoc-in [:gol :cells] (create-cells acorn)))

(defn teardown-gol! []
  (swap! state assoc-in [:gol :busy?] false)
  (swap! state assoc-in [:gol :cells] nil)
  (swap! state assoc-in [:gol :generation] 0))

(defn swap-gol! [cells]
  (console/time-end (str "Generation " (get-in @state [:gol :generation])))
  (swap! state assoc-in [:gol :cells] cells)
  (swap! state update-in [:gol :generation] inc)
  (swap! state assoc-in [:gol :busy?] false))

(defn update-gol! []
  (when-not (get-in @state [:gol :busy?])
    (console/time-start (str "Generation " (get-in @state [:gol :generation])))
    (swap! state assoc-in [:gol :busy?] true)
    (take! (step-chan (get-in @state [:gol :cells])) swap-gol!)))

(defn start-gol! []
  (console/info "start gol")
  (swap! state assoc-in [:app :update-gol?] true))

(defn stop-gol! []
  (console/info "stop gol")
  (swap! state assoc-in [:app :update-gol?] false))


;; -----------------------------------------------------------------------------
;; Render Cycle

(defn update! []
  (when (get-in @state [:app :update-gol?]) (update-gol!)))

(defn render-cells! [context state]
  (let [cell-size 5]
    (set! (.-fillStyle context) "#efefef")
    (.fillRect context 0 0 1000 1000)
    (doseq [[[x y] cell] (get-in state [:gol :cells])
            :let [color (:color cell)
                  rgb (str "rgb(" (:r color) "," (:g color) "," (:b color) ")")]]
      (set! (.-fillStyle context) rgb)
      (.fillRect context (* cell-size x) (* cell-size y) cell-size cell-size))))

(defn render-text! [timestamp state]
  (let [app-name (get-in state [:app :name])
        env-time (get-in state [:env :time])
        f-count (str "F: " (get-in state [:env :frame-count]))
        fps (str "FPS: " (get-in state [:env :frames-per-second]))
        mouse-move (get-in state [:env :mouse-move])
        mouse-x (:client-x mouse-move)
        mouse-y (:client-y mouse-move)
        mouse-pos (str "M: [" mouse-x ":" mouse-y "]")
        prime-count (str "Primes: " (get-in state [:soe :prime-count]))
        max-prime (str "Max Prime: " (get-in state [:soe :max-prime]))
        ppf (str "PPF: " (/ (get-in state [:soe :prime-count]) (get-in state [:env :frame-count])))
        generation (str "Gen: " (get-in state [:gol :generation]))
        population (str "Pop: " (count (get-in state [:gol :cells])))
        display [f-count fps generation population prime-count max-prime]]
    (set! (.-innerText (app-div)) (string/join ", " display))))

(defn render! [timestamp state]
  (render-text! timestamp state)
  (render-cells! (app-context) state))

(defn animate! [timestamp]
  (update!)
  (render! timestamp @state)
  (get-in @state [:app :rendering?]))

(defn start-rendering! []
  (console/info "start rendering")
  (swap! state assoc-in [:app :rendering?] true)
  (poly/listen-animation-frame! animate!))

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
  (setup-soe!)
  (start-rendering!)
;  (start-soe!)
  (start-gol!)
  )

(defn teardown []
  (console/info "teardown")
  (stop-gol!)
  (stop-soe!)
  (stop-rendering!)
  (teardown-soe!)
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
    (goog.object/set canvas "width" 1000)
    (goog.object/set canvas "height" 1000)
    (dom/appendChild (poly/get-body) canvas))
  (setup)
  ;(inspect @state)
  )
