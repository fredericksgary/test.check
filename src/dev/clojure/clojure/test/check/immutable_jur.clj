(ns clojure.test.check.immutable-jur
  "An immutable (and quasi-splittable) version of java.util.Random."
  (:refer-clojure :exclude [next rand-int])
  (:require [clojure.test.check.random :as r]))

;; Hoping to find out if JUR is efficiently splittable.
;;
;; Knuth, 3.2.1 eq 6:
;;   X_{n+k} = (a^kX_n + (a^k - 1)\frac{c}{b}) \mod m
;;
;;   where a is multiplier, b=a-1, and c is increment
;;
;;   Setting k=2:
;;
;;   X_{n+2} = (a^2X_n + (a^2 - 1)\frac{c}{b}) \mod m
;;
;;   Noting that (a^2-1) = (a+1)(a-1) = (a+1)*b this
;;   simplifies to:
;;
;;   X_{n+2} = (a^2X_n + c(a+1)) \mod m
;;

(def ^:const ^:private multiplier 16r5DEECE66D)
(def ^:const ^:private addend 16rB)
(def ^:const ^:private mask (-> 1 (bit-shift-left 48) (dec)))

(defn ^:private initial-scramble
  [^long seed]
  (-> seed (bit-xor multiplier) (bit-and mask)))

(defn ^:private inc-state
  "The core step in the java.util.Random algorithm."
  [^long multiplier ^long addend ^long state]
  (-> state
      (unchecked-multiply multiplier)
      (unchecked-add addend)
      (bit-and mask)))

(defprotocol IJavaUtilRandom
  (next [_] "Returns a new RNG by bumping the state.")
  (rand-int [_] "Returns an Integer."))

(deftype JavaUtilRandom [^long state ^long multiplier ^long addend]

  IJavaUtilRandom
  (next [_] (JavaUtilRandom. (inc-state multiplier addend state)
                             multiplier
                             addend))
  (rand-int [_]
    ;; only uses (intentionally) the top 32 bits of the 48-bit state
    (unchecked-int (bit-shift-right state 16)))

  r/IRandom
  (rand-long [rng]
    (let [rng2 (next rng)]
      ;; haven't checked if this works right
      (+ (bit-shift-left (long (rand-int rng)) 32)
         (rand-int rng2))))
  (split [_]
    (let [state' (inc-state multiplier addend state)
          multiplier' (bit-and mask (unchecked-multiply multiplier
                                                        multiplier))
          addend' (bit-and mask
                           (unchecked-multiply (inc multiplier)
                                               addend))]
      [(JavaUtilRandom. state multiplier' addend')
       (JavaUtilRandom. state' multiplier' addend')])))

(defn make-java-util-random
  [^long seed]
  (JavaUtilRandom. (inc-state multiplier
                              addend
                              (initial-scramble seed))
                   multiplier
                   addend))

(defn JUR->ints
  "Returns an infinite lazy seq of integers from the JUR RNG."
  [rng]
  ((fn self [rng]
     (let [int (rand-int rng)
           rng2 (next rng)]
       (lazy-seq (cons int (self rng2)))))
   rng))

(comment
  ;; The first 12 ints from the builtin JUR
  (let [rng (java.util.Random. 42)]
    (repeatedly 12 #(.nextInt rng)))
  =>
  (-1170105035
   234785527
   -1360544799
   205897768
   1325939940
   -248792245
   1190043011
   -1255373459
   -1436456258
   392236186
   -415012931
   1938135004)

  ;; The first 12 ints from non-splitting use of our JUR
  ;; (identical to above)
  (let [rng (make-java-util-random 42)]
    (take 12 (JUR->ints rng)))

  =>
  (-1170105035
   234785527
   -1360544799
   205897768
   1325939940
   -248792245
   1190043011
   -1255373459
   -1436456258
   392236186
   -415012931
   1938135004)

  ;; The first 12 ints from a four-way split -- note that it's the
  ;; same 12 numbers; each sequence consists of every 4th number, with
  ;; a different starting point
  (let [rng (make-java-util-random 42)
        [rng1 rng2] (r/split rng)
        [rng3 rng4] (r/split rng1)
        [rng5 rng6] (r/split rng2)]
    [(take 3 (JUR->ints rng3))
     (take 3 (JUR->ints rng5))
     (take 3 (JUR->ints rng4))
     (take 3 (JUR->ints rng6))])
  =>
  [(-1170105035 1325939940 -1436456258)
   (234785527 -248792245 392236186)
   (-1360544799 1190043011 -415012931)
   (205897768 -1255373459 1938135004)]

  )
