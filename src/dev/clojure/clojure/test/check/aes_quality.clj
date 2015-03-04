(ns clojure.test.check.aes-quality
  "Debugging dieharder failures of the AES impl."
  (:require [clojure.test.check.prng-comparison :as prngc]
            [clojure.test.check.random :as r]))

;; https://code.google.com/p/dieharder/source/browse/trunk/libdieharder/diehard_bitstream.c?r=494
;;
;; Let's do the non-overlapping variant, seems easier to decompose

(defn longs->w20s
  [the-longs]
  (->> the-longs
       (mapcat (fn [the-long]
                 (->> (iterate #(bit-shift-right % 4) the-long)
                      (map #(bit-and % 15))
                      (take 16))))
       (partition 5)
       (map (fn [xs]
              (->> xs
                   (map-indexed (fn [i x]
                                  (bit-shift-left x (* 4 i))))
                   (reduce +))))))

(defonce all-w20s
  (set (range 16r100000)))

(defn diehard-bitstream
  [the-longs]
  (->> the-longs
       (longs->w20s)
       (take 16r200000)
       (set)
       (clojure.set/difference all-w20s)
       (count)))

(defn some-balanced-AES-longs
  ([] (some-balanced-AES-longs (System/currentTimeMillis)))
  ([seed]
     ((prngc/linearization-strategies :balanced-63)
      (r/make-aes-random seed seed))))

(defn some-balanced-siphash-longs
  ([] (some-balanced-siphash-longs (System/currentTimeMillis)))
  ([seed]
     ((prngc/linearization-strategies :balanced-63)
      (r/make-siphash-random seed))))

(comment

  (def AES-results (atom []))
  (./bg bg-AES
    (./forever
      (swap! AES-results conj
             (diehard-bitstream (some-balanced-AES-longs)))))

  (def siphash-results (atom []))
  (./bg bg-siphash
    (./forever
      (swap! siphash-results conj
             (diehard-bitstream (some-balanced-siphash-longs)))))

  )
