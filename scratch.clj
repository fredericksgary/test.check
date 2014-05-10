
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
  (let [make-tree #(gen/call-gen the-gen (Random. 9174013331171401501) 3)
        shrank-looped (doto (make-tree)
                        ((fn [rose-tree]
                           (dorun (for [rt (rose/children rose-tree)
                                        rt2 (rose/children rt)
                                        rt3 (rose/children rt2)]
                                    42)))))
        direct (make-tree)
        difference-point #(-> % rose/children (nth 3) rose/root)
        shrank-looped' (difference-point shrank-looped)
        direct' (difference-point direct)]
    {:shrank-looped shrank-looped'
     :direct direct'
     :same-args? (= (-> shrank-looped meta :args)
                    (-> direct meta :args))
     :reproduced? (not= shrank-looped' direct')}))

(frequencies (repeatedly 100 repro))
{{:shrank-looped [[0 2 3] [2]], :direct [[0 2 3] [3 2 2]], :same-args? true, :reproduced? true} 100}
