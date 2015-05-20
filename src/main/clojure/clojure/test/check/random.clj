;   Copyright (c) Rich Hickey, Reid Draper, and contributors.
;   All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns ^{:author "Gary Fredericks"
      :doc "Purely functional and splittable pseudo-random number generators."}
  clojure.test.check.random
  (:refer-clojure :exclude [unsigned-bit-shift-right])
  (:require [clojure.test.check.compile-flags :refer [flag!]]))

(defprotocol IRandom
  (split [rng]
    "Returns two new RNGs [rng1 rng2], which should generate
  sufficiently independent random data.

  Note: to maintain independence you should not call split and rand-long
  with the same argument.")
  (rand-long [rng]
    "Returns a random long based on the given immutable RNG.

  Note: to maintain independence you should not call split and rand-long
  with the same argument"))


;; Immutable version of Java 8's java.util.SplittableRandom
;;
;; Meant to give the same results as similar uses of
;; java.util.SplittableRandom, in particular:
;;
;; (= (-> (make-java-util-splittable-random 42)
;;        (rand-long))
;;    (.nextLong (SplittableRandom. 42)))
;;
;; (= (-> (make-java-util-splittable-random 42)
;;        (split)
;;        (first)
;;        (rand-long))
;;    (.nextLong (doto (SplittableRandom. 42)
;;                     (.split))))
;;
;; (= (-> (make-java-util-splittable-random 42)
;;        (split)
;;        (second)
;;        (rand-long))
;;    (.nextLong (.split (SplittableRandom. 42))))
;;
;; Also see the spec that checks this equivalency.


;; backwards compatibility for clojure 1.5
(def ^:private old-clojure?
  (not (resolve 'clojure.core/unsigned-bit-shift-right)))
(defmacro ^:private unsigned-bit-shift-right
  [x n]
  {:pre [(<= 1 n 63)]}
  (if old-clojure?
    (let [mask (-> Long/MIN_VALUE
                   (bit-shift-right (dec n))
                   (bit-not))]
      `(-> ~x
           (bit-shift-right ~n)
           (bit-and ~mask)))
    `(clojure.core/unsigned-bit-shift-right ~x ~n)))

(defmacro ^:private longify
  "Macro for writing arbitrary longs in the java 0x syntax. E.g.
  0x9e3779b97f4a7c15 (which is read as a bigint because it's out
  of range) becomes -7046029254386353131."
  [num]
  (if (> num Long/MAX_VALUE)
    (-> num
        (- 18446744073709551616N)
        (long)
        (bit-or -9223372036854775808))
    num))

(set! *unchecked-math* :warn-on-boxed)

(defmacro ^:private bxoubsr
  "Performs (-> x (unsigned-bit-shift-right n) (bit-xor x))."
  [x n]
  (vary-meta
   `(let [x# ~x]
      (-> x# (unsigned-bit-shift-right ~n) (bit-xor x#)))
   assoc :tag 'long))

(defmacro ^:private mix-64
  [n]
  `(-> ~n
       (bxoubsr 30)
       (* (longify 0xbf58476d1ce4e5b9))
       (bxoubsr 27)
       (* (longify 0x94d049bb133111eb))
       (bxoubsr 31)))

(defmacro ^:private mix-gamma
  [n]
  `(-> ~n
       (bxoubsr 33)
       (* (longify 0xff51afd7ed558ccd))
       (bxoubsr 33)
       (* (longify 0xc4ceb9fe1a85ec53))
       (bxoubsr 33)
       (bit-or 1)
       (as-> z#
             (cond-> z#
                     (> 24 (-> z#
                               (bxoubsr 1)
                               (Long/bitCount)))
                     (bit-xor (longify 0xaaaaaaaaaaaaaaaa))))))

(deftype Pair [rng1 rng2]
  ;; minimal impl for the use in this codebase
  clojure.lang.Indexed
  (nth [_ i]
    (case i 0 rng1 1 rng2))
  (nth [_ i not-found]
    (case i 0 rng1 1 rng2 not-found))
  clojure.lang.Seqable
  (seq [_] (list rng1 rng2)))

(deftype JavaUtilSplittableRandom [^long gamma ^long state]
  IRandom
  (rand-long [_]
    (-> state (+ gamma) (mix-64)))
  (split [this]
    (let [state' (+ gamma state)
          state'' (+ gamma state')
          gamma' (mix-gamma state'')]
      (flag!
       :pair
       (Pair. (JavaUtilSplittableRandom. gamma state'')
              (JavaUtilSplittableRandom. gamma' (mix-64 state')))
       :vector
       [(JavaUtilSplittableRandom. gamma state'')
        (JavaUtilSplittableRandom. gamma' (mix-64 state'))]))))

(defn split-n
  "Returns a collection of n RNGs."
  [^JavaUtilSplittableRandom rng n]
  (case n
    0 []
    1 [rng]
    (let [gamma (.gamma rng)
          n-dec (dec n)]
      (loop [state (.state rng)
             ret (transient [])]
        (if (= n-dec (count ret))
          (-> ret (conj! (JavaUtilSplittableRandom. gamma state)) (persistent!))
          (let [state' (+ gamma state)
                state'' (+ gamma state')
                gamma' (mix-gamma state'')]
            (recur state''
                   (conj! ret (JavaUtilSplittableRandom. gamma' (mix-64 state'))))))))))

(defn build-cache
  "Returns an array of longs of size `cache-size`. The longs will
  be derived by adding gamma to the state, and the even-numbered
  entries will be passed through mix-64."
  [^long state ^long gamma ^long cache-size]
  (let [ret ^longs (make-array Long/TYPE cache-size)]
    (loop [i 0, state state]
      (if (= i cache-size)
        ret
        (let [state1 (+ gamma state)
              state2 (+ gamma state1)]
          (aset ret i (mix-64 state1))
          (aset ret (inc i) state2)
          (recur (+ 2 i) state2))))))

(deftype JavaUtilSplittableRandomCached [^longs cache ^long range-start ^long range-size]
  IRandom
  (rand-long [_]
    (aget cache range-start))
  (split [_]
    (if (= 2 range-size)
      (let [base-state (aget cache range-start)
            base-gamma (mix-gamma (aget cache (inc range-start)))
            state' (+ base-state base-gamma)
            state1 (+ state' base-gamma)
            state2 (mix-64 state')
            gamma2 (mix-gamma state1)]
        (flag!
         :pair
         (Pair. (JavaUtilSplittableRandomCached. (build-cache state1 base-gamma 32) 0 32)
                (JavaUtilSplittableRandomCached. (build-cache state2 gamma2 32) 0 32))
         :vector
         [(JavaUtilSplittableRandomCached. (build-cache state1 base-gamma 32) 0 32)
          (JavaUtilSplittableRandomCached. (build-cache state2 gamma2 32) 0 32)]))
      (let [half-size (bit-shift-right range-size 1)]
        (flag!
         :pair
         (Pair. (JavaUtilSplittableRandomCached. cache range-start half-size)
                (JavaUtilSplittableRandomCached. cache (+ range-start half-size) half-size))
         :vector
         [(JavaUtilSplittableRandomCached. cache range-start half-size)
          (JavaUtilSplittableRandomCached. cache (+ range-start half-size) half-size)])))))

(def ^:private golden-gamma
  (longify 0x9e3779b97f4a7c15))

(defn make-java-util-splittable-random-cached
  [^long seed]
  (JavaUtilSplittableRandomCached. (build-cache seed golden-gamma 32) 0 32))

(defn run-LIJUSR
  [seed total-nums]
  (let [^long gamma golden-gamma]
       (loop [x 0, i 0, ^long state seed]
         (if (= i total-nums)
           x
           (let [state2 (unchecked-add state gamma)
                 x2 (mix-64 state2)]
             (recur (bit-xor x2 x) (inc i) state2))))))

(defn make-java-util-splittable-random
  [^long seed]
  (JavaUtilSplittableRandom. golden-gamma seed))

(defn make-random
  "Given an optional Long seed, returns an object that satisfies the
  IRandom protocol."
  ([] (make-random (System/currentTimeMillis)))
  ([seed] (make-java-util-splittable-random seed)))
