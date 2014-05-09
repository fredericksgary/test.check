
(ns user
  (:require [clojure.pprint :refer [pprint pp]]
            [clojure.set :as sets]
            [clojure.test.check :refer [retry]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.rose-tree :as rose]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :refer [defspec]]))

(def things
  (gen/bind (gen/vector gen/nat)
            (fn [v1]
              (gen/fmap (fn [v2]
                          [v1 v2])
                        (gen/vector gen/nat)))))

;; Reproducing IOOBE:
;; (poop 1000 :seed 999)
;; (retry poop (-> *1 :shrunk :key))
(defspec poop 100
  (prop/for-all [[v1 v2] things]
    (not (sets/subset? #{1 3 5 7 9 11 13 15 17 19 21} (set (concat v1 v2))))))


;;
;; Messin w/ it
;;

(def k [4989522275755491824 120 [2 4 1 1 3 20 3 11 2 0 10 2 2 4 18 5 2 4 4 1 3 1 17 3 8 1 18 8 3 10 6 6 6 0 5 11 21 4 4 40 17 95 74 38 8 50 75 92 107 67 128 297 191 73 317 146 120 25 15 357 16 32 85 18 257 13 191 332 10 0 16 39 64 141 168 86 7 50 29 30 14 15 47 1 133 126 173 321 0 0 0 0 0 0 1 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 3 3 3 3 3 3 3 3 3 3 3 3 3 3 4 4 4 4 4 4 4 4 4 4 4 4 4 4 5 5 5 5 5 5 5 5 5 5 5 5 5 5 5 5 5 5 5 5 5 5 5 5 5 5 5 5 6]])

(for [i (range (count (last k)))]
  (try (retry poop (update-in k [2] #(subvec % 0 i)))
       (catch Exception e e)))

;;
;; Shrinking the failing case
;;
;; Shrunk to #{1} 92392573 4
;;

(def ^:dynamic *num-set*)

(defspec poop-2 100
  (prop/for-all [[v1 v2] things]
    (not (sets/subset? *num-set* (set (concat v1 v2))))))

(defn iiobe?
  [num-set seed max-size]
  (binding [*num-set* num-set
            *out* (clojure.java.io/writer "/dev/null")]
    (if-let [{:keys [key]} (:shrunk (poop-2 1000 :seed seed :max-size max-size))]
      (try (retry poop-2 key)
           false
           (catch IndexOutOfBoundsException e true))
      false)))

(defn find-seed
  [num-set max-size]
  (reduce (fn [_ seed]
            (and
             (iiobe? num-set seed max-size)
             (reduced seed)))
          nil
          (repeatedly 100 #(rand-int 100000000))))

;;
;; Shrankest
;;

(defspec poop-3 100
  (prop/for-all [[v1 v2] things]
    (not (sets/subset? #{1} (set (concat v1 v2))))))

(retry poop-3
       (-> (poop-3 1000 :seed 92392573 :max-size 4)
           :shrunk
           :key))


(def db1 clojure.test.check/debug-quick-check)
(def db1 (clojure.test.check/quook-chook
          1000
          (-> poop-3 meta :property)
          :seed 92392573
          :max-size 4))
(def db2 (gen/call-key-with-meta (-> poop-3 meta :property) [9174013331171401501 3 []]))

(defn walk-rose
  [rose path]
  (if (empty? path)
    (rose/root rose)
    (recur (nth (rose/children rose) (first path)) (rest path))))

(defn walk-rose-children
  [rose path]
  (if (empty? path)
    (rose/children rose)
    (recur (nth (rose/children rose) (first path)) (rest path))))

(= (-> db1 meta :args)
   (-> db2 meta :args))

(-> db1 rose/children (nth 3) rose/root :args) [[[0 2 3] [2 3 3]]]
(-> db2 rose/children (nth 3) rose/root :args) [[[0 2 3] [3 2 2]]]


(-> poop-3
    meta
    :property
    (gen/call-key-with-meta [9174013331171401501 3 []])
    rose/children
    (nth 3)
    rose/root
    :args)

;;
;; Getting to the root of the matter?
;;

(def the-prop
  (prop/for-all [[v1 v2] (gen/bind (gen/vector gen/nat)
                                   (fn [v1]
                                     (gen/fmap (fn [v2]
                                                 [v1 v2])
                                               (gen/vector gen/nat))))]
    (not (sets/subset? #{1} (set (concat v1 v2))))))

(defn repro
  []
  (clojure.test.check/quick-check 1000
                                  the-prop
                                  :seed 92392573
                                  :max-size 4)
  (let [via-quick-check clojure.test.check/dbg
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
