(ns labyrinth.model)

;; field structure example
;; {:rivers [[[1 1] [2 1] [2 2] [3 2]]]
;;  :holes [[[1 2] [2 3]]
;;          [[3 1] [3 3]]]
;;  :mirrors [[1 3]]
;;  :inner-walls #{#{[1 1] [1 2]}
;;                 #{[1 2] [2 2]}
;;                 #{[2 2] [2 3]}}}

(def step-directions [[0 -1] [0 1] [-1 0] [1 0]])

;; takes a random item from sequence: (1 2 3 4 5) -> 2
(defn- random-element [s] (when-not (empty? s) (-> s seq rand-nth)))

;; pops a random item from set!: [1 2 3 4 5] -> [2 [1 3 4 5]]
(defn- pop-random-element [s]
  (let [e (random-element s)]
    [e (disj s e)]))

(defn- ntimes [n f v]
  (loop [n n
         v v]
    (if (pos? n) (recur (dec n) (f v)) v)))

(defn- create-not-checked-field [{:keys [max-r max-c rivers-count river-length holes-count mirrors-count inner-walls-count]}]
  (let [in-field? (fn [[r c]] (and (<= 1 r max-r) (<= 1 c max-c)))

        ;; give free cells set to the field, the neighbors given a horizontal / vertical
        get-free-neighbours (fn [p free-cs]
                              (->> step-directions
                                   (keep (fn [d]
                                           (let [p' (mapv + p d)]
                                             (when (free-cs p') p'))))
                                   set))

        ;; add another cage to the river, splitting it off free:
        ;; [[7 3] #{1 2 4 5 6}] -> [[7 3 4] #{1 2 5 6}]
        add-next-river-cell (fn [[river free-cs]]
                              (let [cs-to-choose (if (empty? river) free-cs (get-free-neighbours (last river) free-cs))]
                                (when-not (empty? cs-to-choose)
                                  (let [p (random-element cs-to-choose)]
                                    [(conj river p) (disj free-cs p)]))))

        ;; dial a river of a given length: [[] #{1 2 3 4 5 6 7}] -> [[1 4 7 3] #{2 5 6}]
        get-river (fn [len river-free-cs] (ntimes len add-next-river-cell river-free-cs))

        ;; try to dial a river of a given length by limiting the number of failed attempts
        try-get-river (fn [trys len river-free-cs]
                        (when (pos? trys)
                          (or (get-river len river-free-cs)
                              (recur (dec trys) len river-free-cs))))

        ;; add another river to the list of rivers, reducing the set of free cells
        add-river (fn [[rivers free-cs]]
                    (when-let [[river free-cs] (try-get-river 50 river-length [[] free-cs])]
                      [(conj rivers river) free-cs]))

        ;; add another hole to the list of holes, reducing the set of free cells
        add-hole (fn [[holes free-cs]]
                   (when (>= (count free-cs) 2)
                     (let [[a free-cs] (pop-random-element free-cs)
                           [b free-cs] (pop-random-element free-cs)]
                       [(conj holes [a b]) free-cs])))

        ;; add another selected cell to the list of cells, reducing the set of free cells
        ;; [[7 3] #{1 2 4 5 6}] -> [[7 3 4] #{1 2 5 6}]
        add-random-elem (fn [[selected free-cs]]
                          (when-not (empty? free-cs)
                            (let [[e free-cs] (pop-random-element free-cs)]
                              [(conj selected e) free-cs])))

        all-cells (set (for [r (range max-r)
                             c (range max-c)]
                         [(inc r) (inc c)]))

        [rivers  free-cs] (ntimes rivers-count add-river        [[] all-cells])
        [holes   free-cs] (ntimes holes-count add-hole          [[] free-cs])
        [mirrors free-cs] (ntimes mirrors-count add-random-elem [[] free-cs])

        ;; inner walls generation
        wall-crosses-river (fn [w [r1 r2 :as r]]
                             (cond (empty? (rest r)) false
                                   (= w #{r1 r2}) true
                                   :else (recur w (rest r))))
        valid-inner-walls (->> all-cells
                               (mapcat (fn [[r c :as p]]
                                         (->> [[r (inc c)] [(inc r) c]]
                                              (filter in-field?)
                                              (map #(hash-set p %)))))
                               (remove (fn [w] (some (fn [r] (wall-crosses-river w r)) rivers)))
                               set)
        inner-walls (-> (ntimes inner-walls-count add-random-elem [[] valid-inner-walls])
                        first
                        set)]

    (when free-cs ;; not nil?
      {:max-r max-r
       :max-c max-c
       :rivers rivers
       :holes holes
       :mirrors mirrors
       :inner-walls inner-walls})))


;; obtaining the second hole coordinate by the first
(defn- co-hole [p [h1 h2]] (cond (= p h1) h2 (= p h2) h1))

;; search for the transferred position in the list of objects (rivers or holes), returns the applied visitor
(defn- get-by-p [p objects f]
  (loop [[o & objs] objects
         i 1]
    (when o
      (let [j (.indexOf o p)]
        (if (<= 0 j)
          (f o i j)
          (recur objs (inc i)))))))

;; moving in the specified direction
(defn move [[r0 c0 :as p0] [dr dc] {:keys [max-r max-c rivers holes mirrors inner-walls]}]
  (let [mirror-factor (if (-> mirrors set (get p0)) -1 1)
        r (+ r0 (* dr mirror-factor))
        c (+ c0 (* dc mirror-factor))
        p [r c]]
    (if (and (<= 1 r max-r)
             (<= 1 c max-c)
             (not (get inner-walls (hash-set p0 p))))
      (or
       (get-by-p p rivers (fn [river _ j] (let [pos (last river)]
                                            {:response "river"
                                             :animate-cell-seq (subvec river j)
                                             :pos pos})))
       (get-by-p p holes (fn [hole i _] (let [pos (co-hole p hole)]
                                          {:response (str "hole " i)
                                           :animate-cell-seq [p pos]
                                           :pos pos})))
       {:response "free"
        :pos p})
      {:response "wall"
       :pos p0})))

;; get detailed cell data for ui puposes
(defn cell-data [[r c :as p] {:keys [max-r max-c rivers holes mirrors]}]
  (when (and (<= 1 r max-r)
             (<= 1 c max-c)
             )
    (or
     (when (-> mirrors set (get p)) {:cell-type :mirror})
     (get-by-p p rivers (fn [river i j] {:cell-type :river
                                         :river river
                                         :obj-index i
                                         :cell-index j}))
     (get-by-p p holes (fn [hole i j] {:cell-type :hole
                                       :hole hole
                                       :obj-index i
                                       :cell-index j}))
     {:cell-type :free})))

;; for ui puposes
(defn inner-wall? [p1 p2 {:keys [inner-walls]}]
  (get inner-walls (hash-set p1 p2)))


;; field test function
(defn- correct-field? [{:keys [rivers-count river-length]} {:keys [max-r max-c] :as field}]
  (and field
       (let [max-reached-points-amount (- (* max-r max-c) (* rivers-count (max 0 (dec river-length))))

             ;; precalculated moves-pos map
             moves-pos-map (->> (for [r (range max-r)
                                      c (range max-c)
                                      d (conj step-directions [0 0])
                                      :let [p0 [(inc r) (inc c)]
                                            p (:pos (move p0 d field))]
                                      :when p]
                                  [[p0 d] p])
                                (into {}))

             ;;  move-pos (fn [p0 d] (:pos (move p0 d field)))
             move-pos (fn [p0 d] (get moves-pos-map [p0 d]))

             ;; search of achievable points from the transferred items
             go (fn [currs achieved #_checked]
                  ;; (or (some checked currs)
                  (let [nexts (for [p0 currs
                                    d step-directions
                                    :let [p (move-pos p0 d)]
                                    :when (and p (not (get achieved p)))]
                                p)]
                    (if (empty? nexts)
                      (= max-reached-points-amount (count achieved))
                      (recur nexts (into achieved nexts) #_checked))))

             ;; From the point all the others are reachable
             correct-point? (fn [p #_checked] (let [start-point (move-pos p [0 0])]
                                                (go [start-point] #{start-point} #_checked)))]

         ;; From every point of the field all the others are reachable
         #_(loop [[p & ps] (for [r (range max-r)
                               c (range max-c)]
                           [(inc r) (inc c)])
                checked #{}]
           (cond (nil? p) true
                 (correct-point? p checked) (recur ps (conj checked p))
                 :else false))

         ;; with 'checked' set used above field below was checked as correct!!!
         ;; {:max-r 4, :max-c 4,
         ;;  :rivers [[[2 4] [1 4] [1 3] [1 2]]
         ;;           [[4 3] [4 4] [3 4] [3 3]]],
         ;;  :holes [[[3 2] [1 1]] [[4 1] [2 1]]],
         ;;  :mirrors [[3 1]],
         ;;  :inner-walls #{#{[1 1] [1 2]} #{[1 1] [2 1]} #{[3 1] [4 1]} #{[2 2] [2 3]} #{[4 2] [4 3]}}}

         (every? correct-point? (for [r (range max-r)
                                      c (range max-c)]
                                  [(inc r) (inc c)])))))


;; field generation function
(defn new-field [{:keys [max-r max-c rivers-count river-length holes-count mirrors-count inner-walls-count start-r start-c] :as settings}]
  (let [total-cells (* max-r max-c)
        objects-cells (+ (* rivers-count river-length) (* 2 holes-count) mirrors-count)
        max-inner-walls-count (- (+ (* max-r (dec max-c)) (* max-c (dec max-r)))
                                 (* rivers-count (max 0 (dec river-length))))]
    ;; checking incoming data and generating a field
    (cond
      (zero? (+ objects-cells inner-walls-count)) {:error "empty map"}
      (< total-cells objects-cells) {:error (str objects-cells " > " max-r "*" max-c)}
      (< max-inner-walls-count inner-walls-count) {:error (str "inner walls > " max-inner-walls-count)}
      (not (and (<= 1 start-r max-r) (<= 1 start-c max-c))) {:error "start point"}
      :else (loop [n 10]
              (if (zero? n)
                {:fail "try again"}
                (let [field (create-not-checked-field settings)]
                  (if (correct-field? settings field) {:field field} (recur (dec n)))))))))


;; minimal steps count from start point to all the others ;
(defn calculate-minimal-steps-count-map [start-point field]
  (let [;; list of achievable points from the transferred items
        points-reached (fn [currs m i]
                         (let [nexts (for [p0 currs
                                           d step-directions
                                           :let [p (:pos (move p0 d field))]
                                           :when (and p (not (get m p)))]
                                       p)]
                           (if (empty? nexts) m (recur nexts (reduce (fn [acc p] (assoc acc p (inc i))) m nexts) (inc i)))))]
    (points-reached [start-point] {start-point 0} 0)))
