
(ns user
  (:require [clojure.pprint :refer [pprint pp]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.rose-tree :as rose]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :refer [defspec]]))


;;
;; Getting to the root of the matter?
;;

(def the-prop
  (prop/for-all [[v1 v2] (gen/bind (gen/vector gen/nat)
                                   (fn [v1]
                                     (gen/fmap (fn [v2]
                                                 [v1 v2])
                                               (gen/vector gen/nat))))]
    (not (some #{1} (concat v1 v2)))))

(defn repro
  []
  (let [shrank-looped (doto (gen/call-key-with-meta the-prop [9174013331171401501 3 []])
                        (clojure.test.check/shrink-loop))
        direct (gen/call-key-with-meta the-prop [9174013331171401501 3 []])
        difference-point #(-> % rose/children (nth 3) rose/root :args)
        shrank-looped' (difference-point shrank-looped)
        direct' (difference-point direct)]
    {:shrank-looped shrank-looped'
     :direct direct'
     :same-args? (= (-> shrank-looped meta :args)
                    (-> direct meta :args))
     :reproduced? (not= shrank-looped' direct')}))

(frequencies (repeatedly 100 repro))
{{:shrank-looped [[[0 2 3] [2 3 3]]], :direct [[[0 2 3] [3 2 2]]], :same-args? true, :reproduced? true} 100}
