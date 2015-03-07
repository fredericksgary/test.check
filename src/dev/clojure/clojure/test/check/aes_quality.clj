(ns clojure.test.check.aes-quality
  "Debugging dieharder failures of the AES impl."
  (:require [clojure.test.check.prng-comparison :as prngc]
            [clojure.test.check.random :as r])
  (:import [org.apache.commons.math3.distribution NormalDistribution]))

;; https://code.google.com/p/dieharder/source/browse/trunk/libdieharder/diehard_bitstream.c?r=494
;;
;; Let's do the non-overlapping variant, seems easier to decompose

(def expected-mean 141909)
;; how on earth do we use this?
(def expected-sigma 290.0)

(defn stats
  [xs]
  (let [c              (double (count xs))
        mean           (/ (reduce + xs) c)
        variance       (/ (->> xs
                               (map #(- mean %))
                               (map #(* % %))
                               (reduce +))
                          c)
        sigma          (Math/sqrt variance)
        standard-error (/ expected-sigma (count xs))
        z-score        (/ (- mean expected-mean) standard-error)
        cdf-value      (.cumulativeProbability
                        (NormalDistribution.)
                        z-score)
        p-value        (-> cdf-value
                           (cond->> (> cdf-value 0.5)
                                    (- 1))
                           (* 2))]
    {:mean     mean
     :sigma    sigma
     :variance variance
     :z-score  z-score
     :p-value  p-value}))

(defn longs->non-overlapping-w20s
  [the-longs]
  (->> the-longs
       (drop 500000)
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

(defn longs->w20s
  [the-longs]
  (->> the-longs
       (drop 500000) ;; dieharder testing the speed
       (mapcat (fn [the-long]
                 (->> (iterate #(bit-shift-right % 8) the-long)
                      (map #(bit-and % 255))
                      (take 8)
                      (partition 4)
                      (reverse)
                      (apply concat))))
       (drop 1) ;; dieharder discards first byte incidentally
       (mapcat (fn [the-byte]
                 (->> (iterate #(bit-shift-right % 1) the-byte)
                      (map #(bit-and % 1))
                      (take 8)
                      (reverse))))
       (partition 20 1)
       (map (fn [bits]
              (loop [the-long 0
                     bits bits]
                (if (empty? bits)
                  the-long
                  (recur (bit-or (bit-shift-left the-long 1)
                                 (first bits))
                         (rest bits))))))))

(defn diehard-bitstream
  [the-longs]
  (->> the-longs
       (longs->w20s)
       (take 16r200000)
       (distinct)
       (count)
       (- 16r100000)))

(defn diehard-non-overlapping-bitstream
  [the-longs]
  (->> the-longs
       (longs->non-overlapping-w20s)
       (take 16r200000)
       (distinct)
       (count)
       (- 16r100000)))

(defn some-balanced-AES-longs
  ([] (some-balanced-AES-longs (System/currentTimeMillis)))
  ([seed]
     ((prngc/linearization-strategies :balanced-63)
      (r/make-aes-random seed seed))))

(defn some-linear-AES-longs
  ([] (some-balanced-AES-longs (System/currentTimeMillis)))
  ([seed]
     ((prngc/linearization-strategies :left-linear)
      (r/make-aes-random seed seed))))

(defn some-balanced-siphash-longs
  ([] (some-balanced-siphash-longs (System/currentTimeMillis)))
  ([seed]
     ((prngc/linearization-strategies :balanced-63)
      (r/make-siphash-random seed))))
