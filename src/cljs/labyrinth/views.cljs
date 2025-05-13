(ns labyrinth.views
  (:require [reagent.core :as r :include-macros true]
            [labyrinth.model :as model]
            [labyrinth.widgets :as ws]))

(def cell-border-width "2px")

(def river-width-% 16) ;; %

(def river-parts
  {:top
   [:div {:style {:background-color :blue
                  :position :absolute
                  :width (str river-width-% "%")
                  :height (str (/ (+ 100 river-width-%) 2) "%")
                  :left (str (/ (- 100 river-width-%) 2) "%")
                  :top (str "-" cell-border-width)
                  :border-top (str cell-border-width " solid blue")}}]
   :right
   [:div {:style {:background-color :blue
                  :position :absolute
                  :width (str (/ (+ 100 river-width-%) 2) "%")
                  :height (str river-width-% "%")
                  :top (str (/ (- 100 river-width-%) 2) "%")
                  :right (str "-" cell-border-width)
                  :border-right (str cell-border-width " solid blue")}}]
   :bottom
   [:div {:style {:background-color :blue
                  :position :absolute
                  :width (str river-width-% "%")
                  :height (str (/ (+ 100 river-width-%) 2) "%")
                  :left (str (/ (- 100 river-width-%) 2) "%")
                  :bottom (str "-" cell-border-width)
                  :border-bottom (str cell-border-width " solid blue")}}]
   :left
   [:div {:style {:background-color :blue
                  :position :absolute
                  :width (str (/ (+ 100 river-width-%) 2) "%")
                  :height (str river-width-% "%")
                  :top (str (/ (- 100 river-width-%) 2) "%")
                  :left (str "-" cell-border-width)
                  :border-left (str cell-border-width " solid blue")}}]})

(defn- cell [[r c :as p] {:keys [max-r max-c] :as field} cell-size show-field? cur-pos?]
  (let [cell-size-px (str cell-size "px")
        border (fn [r1 c1] (str cell-border-width " solid "
                                (if (and (<= 1 r1 max-r)
                                         (<= 1 c1 max-c))
                                  (if (and show-field? (model/inner-wall? p [r1 c1] field))
                                    "black" ;; inner wall
                                    "aliceblue") ;; space between cells
                                  "black"))) ;; outer wall
        {:keys [cell-type] :as cell-data} (when show-field? (model/cell-data p field))]
    [:div {:style {:width cell-size-px
                   :height cell-size-px
                   :font-size cell-size-px
                   :background-color (if cur-pos? "#ffffc8" :lavender)
                   :position :relative
                   :border-top    (border (dec r) c)
                   :border-right  (border r (inc c))
                   :border-bottom (border (inc r) c)
                   :border-left   (border r (dec c))
                   :display :flex
                   :justify-content :center
                   :align-items :center}}
     (case cell-type
       :river (let [{:keys [river cell-index]} cell-data
                    {:keys [top right bottom left]} river-parts
                    m {[(dec r) c] top, [r (inc c)] right, [(inc r) c] bottom, [r (dec c)] left}]
                [:<>
                 (get m (get river (dec cell-index)))
                 (get m (get river (inc cell-index)))
                 ;; lake
                 (when (= cell-index (-> river count dec))
                   [:div {:style {:background-color :blue
                                  :position :absolute
                                  :width "50%"
                                  :height "50%"
                                  :top "25%"
                                  :left "25%"
                                  :border-radius "50%"}}])])

       :hole [:div {:style {:font-size "80%"
                            :font-weight :bold
                            :display :flex
                            :justify-content :center
                            :align-items :center}}
              (-> cell-data :obj-index str)]

       :mirror [:div {:style {:background-color :deepskyblue
                              :position :absolute
                              :width "50%"
                              :height "50%"
                              :top "25%"
                              :left "25%"
                              :transform "rotate(45deg)"}}]
       nil)]))

(defn- map-view [{:keys [field cell-size show-field? cur-pos start-pos]}]
  (when field
    (let [{:keys [max-r max-c]} field]
      [:div {:style {:border (str cell-border-width " solid black")
                     :width :fit-content}}
       (map (fn [r]
              [:div {:key r
                     :style {:display :flex
                             :width :fit-content}}
               (map (fn [c]
                      (let [p [(inc r) (inc c)]
                            cur-pos? (= p (if show-field? cur-pos start-pos))]
                        ^{:key c} [cell p field cell-size show-field? cur-pos?]))
                    (range 0 max-c))])
            (range 0 max-r))])))

;; service functions

(defn- anti-jitter [timeout on-click]
  (fn [e]
    (let [button (.-target e)]
      (.setAttribute button "disabled" true)
      (on-click)
      (js/setTimeout #(.removeAttribute button "disabled") timeout))))

(defn- periodic [f v timeout]
  (-> (js/Promise. (fn [resolve] (js/setTimeout #(resolve (f v)) timeout)))
      (.then #(when % (periodic f v 200)))
      (.catch prn)))

(defn- animate-move [state!]
  (when-let [animate-cell-seq (-> @state! :animate-cell-seq not-empty)]
    (let [[p & ps] animate-cell-seq]
      (swap! state! assoc
             :cur-pos p
             :animate-cell-seq ps)
      (seq ps))))

(defn- move [state! d]
  (let [{:keys [field show-field? cur-pos steps-count minimal-steps-count-map]} @state!
        {:keys [response pos animate-cell-seq]} (when field (model/move cur-pos d field))
        animate? (and show-field? animate-cell-seq)
        steps-count ((fnil inc 0) steps-count)
        label-sub-color (when (and minimal-steps-count-map
                                   (> steps-count (get minimal-steps-count-map pos)))
                          :red)]
    (swap! state! assoc
           :label-sup response
           :cur-pos (if animate? cur-pos pos)
           :animate-cell-seq (when animate? animate-cell-seq)
           :steps-count steps-count
           :label-sub steps-count
           :label-sub-color label-sub-color)
    (when animate?
      (periodic animate-move state! 1))))


(defn- control-panel [state!]
  (let [{:keys [field label-sup label-sub label-sub-color animate-cell-seq]} @state!]
    [:div.block-panel
     [:div.bold4rem (str label-sup)]
     [:fieldset.control-buttons (when (or (not field) (seq animate-cell-seq)) {:disabled true})
      [:div] [:button.bold3rem {:on-click (anti-jitter 200 #(move state! [-1 0]))} "^"]
      [:div] [:button.bold3rem {:on-click (anti-jitter 200 #(move state! [0 -1]))} "<"]
      [:div] [:button.bold3rem {:on-click (anti-jitter 200 #(move state! [0  1]))} ">"]
      [:div] [:button.bold3rem {:on-click (anti-jitter 200 #(move state! [1  0]))} "v"]
      [:div]]
     [:div.bold4rem {:style {:color (or label-sub-color :black)}} (str label-sub)]

     [:div {:style {:display :flex}}
      [:label "map size"]
      [ws/input-range {:state state!
                       :min 20
                       :max 250
                       :path [:cell-size]}]]]))

(defn- input-integer [k min-val max-val state!]
  [ws/input-integer {:state state!
                     :path [:settings k]
                     :min min-val
                     :max max-val
                     :style {:outline :none}}])

(defn- settings-panel [state!]
  (let [{:keys [field show-field?]} @state!]
    [:div.block-panel
     [:div.inputs-panel
      [:label.input-label "rows"] [input-integer :max-r 2 10 state!]
      [:label.input-label "cols"] [input-integer :max-c 2 10 state!]
      [:label.input-label "rivers count"] [input-integer :rivers-count 0 10 state!]
      [:label.input-label "river length"] [input-integer :river-length 1 20 state!]
      [:label.input-label "holes count"] [input-integer :holes-count 0 50 state!]
      [:label.input-label "mirrors count"] [input-integer :mirrors-count 0 10 state!]
      [:label.input-label "inner walls count"] [input-integer :inner-walls-count 0 10 state!]
      [:label.input-label "start row"] [input-integer :start-r 1 10 state!]
      [:label.input-label "start col"] [input-integer :start-c 1 10 state!]]
     [:div.settings-buttons
      [:button.bold2rem
       {:on-click (anti-jitter
                   200
                   (fn []
                     (let [{:keys [settings]} @state!
                           {:keys [start-r start-c]} settings
                           {:keys [field error fail]} (model/new-field settings)]
                       (if field
                         (do
                           (swap! state! assoc
                                  :field field
                                  :minimal-steps-count-map nil
                                  :steps-count nil
                                  :cur-pos [start-r start-c])
                           (move state! [0 0]))
                         (swap! state! (fn [state] (-> state
                                                       (select-keys [:settings :cell-size :show-field?])
                                                       (assoc :label-sup (if error "ERROR" "oops!..")
                                                              :label-sub (or error fail)))))))))}
       "New game"]
      [:button.bold2rem
       {:on-click (anti-jitter 200 (fn [] (swap! state! update :show-field? not)))}
       (if show-field? "Hide map" "Show map")]
      [:div]
      [:button.bold2rem
       (cond->
        {:on-click (anti-jitter
                    200
                    (fn []
                      (let [{:keys [cur-pos field]} @state!
                            minimal-steps-count-map (model/calculate-minimal-steps-count-map cur-pos field)]
                        (swap! state! assoc
                               :minimal-steps-count-map minimal-steps-count-map
                               :steps-count 0
                               :label-sub 0
                               :label-sub-color nil))))}
         (not field) (assoc :disabled true))
       "Check steps"]]
     ;; [:div {:style {:width "300px"}} (str @state!)]
     ]))

(defn main-component []
  (r/with-let [state! (r/atom {:settings {:max-r 4
                                          :max-c 4
                                          :rivers-count 2
                                          :river-length 4
                                          :holes-count 2
                                          :mirrors-count 1
                                          :inner-walls-count 5
                                          :start-r 1
                                          :start-c 1}
                               :cell-size 100
                               :label-sup "New"
                               :label-sub "game?"})]
    [:div.main-component
     [settings-panel state!]
     [control-panel state!]
     [map-view (let [{:keys [settings] :as state} @state!
                     {:keys [start-r start-c]} settings]
                 (-> state
                     (select-keys [:field :cell-size :show-field? :cur-pos])
                     (assoc :start-pos [start-r start-c])))]]))
