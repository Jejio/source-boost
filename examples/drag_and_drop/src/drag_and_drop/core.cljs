
(ns ^:figwheel-always drag-and-drop.core
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [jamesmacaulay.zelkova.signal :as z]
            [jamesmacaulay.zelkova.mouse :as mouse]))

(enable-console-print!)

(def init-state {:boxes []
                 :drag nil})

(defn pos->hue
  [[x y]]
  (mod (+ (/ x 2) (/ y 2)) 360))

(defn build-box
  [[x1 y1] [x2 y2]]
  (let [[top bottom] (sort [y1 y2])
        [left right] (sort [x1 x2])
        centre [(/ (+ left right) 2)
                (/ (+ top bottom) 2)]]
    {:top top
     :left left
     :width (- right left)
     :height (- bottom top)
     :hue (pos->hue centre)
     :resizing? true}))

(defn in-box?
  [[x y]
   {:keys [top left width height]}]
  (and (< left x (+ left width))
       (< top y (+ top height))))

(defn moving?
  [box]
  (contains? box :drag-offset))

(defn resizing?
  [box]
  (:resizing? box))

(defn click
  [pos state]
  (let [without-clicked (remove (partial in-box? pos))]
    (update-in state [:boxes] (partial into [] without-clicked))))

(defn topleft-pos
  [{:keys [left top]}]
  [left top])

(defn start-dragging-box-from-pos
  [pos box]
  (let [offset (->> box (topleft-pos) (map - pos))]
    (assoc box :drag-offset offset)))

(defn start-drag
  [pos state]
  (let [drag-target? (partial in-box? pos)
        {targets true non-targets false} (->> state :boxes (group-by drag-target?))
        boxes' (into [] (concat non-targets
                                (map (partial start-dragging-box-from-pos pos) targets)
                                (when (empty? targets) [(build-box pos pos)])))]
    (assoc state
      :boxes boxes'
      :drag {:start-pos pos})))

(defn drag
  [pos state]
  (let [box-category #(cond (moving? %) :moving
                            (resizing? %) :resizing
                            :else :placed)
        {:keys [placed moving resizing]} (group-by box-category (:boxes state))
        drag-to-pos (fn [box]
                      (let [[left top] (map - pos (:drag-offset box))]
                        (assoc box :left left :top top)))
        boxes' (into [] (concat placed
                                (map drag-to-pos moving)
                                (map (fn [_] (build-box pos (-> state :drag :start-pos)))
                                     resizing)))]
    (assoc state :boxes boxes')))

(defn drop-boxes
  [state]
  (let [drop-box (fn [box] (dissoc box :drag-offset :resizing?))]