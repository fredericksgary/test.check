
(ns user
  (:require [clojure.pprint :refer [pprint pp]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.rose-tree :as rose])
  (:import java.util.Random))


;;
;; Getting to the root of the matter?
;;

(def the-gen (gen/bind (gen/vector gen/nat)
                       (fn [v1]
                         (gen/fmap (fn [v2]
                                     [v1 v2])
                                   (gen/vector gen/nat)))))

(defn repro
  []
  (let [make-tree #(gen/call-gen the-gen (Random. 91740133311714010) 3)
        shrank-looped (doto (make-tree)
                        ((fn [rose-tree]
                           (dorun (for [rt (rose/children rose-tree)
                                        rt2 (rose/children rt)
                                        rt3 (rose/children rt2)]
                                    42)))))
        direct (make-tree)
        difference-point #(->> % rose/children (map rose/root))
        shrank-looped' (difference-point shrank-looped)
        direct' (difference-point direct)]
    {:shrank-looped shrank-looped'
     :direct direct'
     :same-args? (= (-> shrank-looped meta :args)
                    (-> direct meta :args))
     :reproduced? (not= shrank-looped' direct')}))

(frequencies (repeatedly 100 repro))
{{:shrank-looped ([[3 0] [2]] [[3 0] []] [[3 3] [3 2 2]] [[0 3 0] []] [[2 3 0] [1 2 2]] [[3 0 0] []] [[3 2 0] [2 1 2]] [[3 3 0] [2 1]] [[3 3 0] [2 1]] [[3 3 0] [2 2]] [[3 3 0] [0 2 1]] [[3 3 0] [1 2 1]] [[3 3 0] [2 0 1]] [[3 3 0] [2 1 1]] [[3 3 0] [2 2 0]]), :direct ([[3 0] [2]] [[3 0] []] [[3 3] [3 2 2]] [[0 3 0] [0]] [[2 3 0] [1]] [[3 0 0] [2 0]] [[3 2 0] [1]] [[3 3 0] [2 1]] [[3 3 0] [2 1]] [[3 3 0] [2 2]] [[3 3 0] [0 2 1]] [[3 3 0] [1 2 1]] [[3 3 0] [2 0 1]] [[3 3 0] [2 1 1]] [[3 3 0] [2 2 0]]), :same-args? true, :reproduced? true} 100}
