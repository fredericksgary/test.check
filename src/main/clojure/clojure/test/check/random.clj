(ns clojure.test.check.random
  (:refer-clojure :exclude [double]))

(set! *warn-on-reflection* true)

(defprotocol IRandom
  (double [rnd] "Returns [double next-rnd].")
  (split [rnd] "Returns [next-rnd another-rnd]"))

(defrecord Random [^long seed]
  IRandom
  (double [_]
    ;; TODO; better impl?
    (let [rnd (java.util.Random. seed)]
      [(.nextDouble rnd) (Random. (.nextLong rnd))]))
  (split [_]
    (let [rnd (java.util.Random. seed)]
      [(Random. (.nextLong rnd)) (Random. (.nextLong rnd))])))

(defn random
  ([] (->Random (rand-int 1000000000)))
  ([seed] (->Random seed)))

(defn random-seq
  "Returns an infinite lazy seq of rands."
  [seed]
  (let [rnd (java.util.Random. seed)]
    (repeatedly #(Random. (.nextLong rnd)))))
