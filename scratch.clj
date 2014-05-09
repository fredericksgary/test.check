
(ns user
  (:require [clojure.pprint :refer [pprint pp]]
            [clojure.test.check :refer [retry]]
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
  (let [via-quick-check (:result-map-rose (clojure.test.check/quick-check 1000
                                                                          the-prop
                                                                          :seed 92392573
                                                                          :max-size 4))
        direct (gen/call-key-with-meta the-prop [9174013331171401501 3 []])
        difference-point #(-> % rose/children (nth 3) rose/root :args)
        via-quick-check' (difference-point via-quick-check)
        direct' (difference-point direct)]
    {:via-quick-check via-quick-check'
     :direct direct'
     :same-args? (= (-> via-quick-check meta :args)
                    (-> direct meta :args))
     :reproduced? (not= via-quick-check' direct')}))

(frequencies (repeatedly 100 repro))
{{:via-quick-check [[[0 2 3] [2 3 3]]], :direct [[[0 2 3] [3 2 2]]], :same-args? true, :reproduced? true} 100}
