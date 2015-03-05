(ns clojure.test.check.immutable-jur
  "Implementing an immutable version of JUR."
  (:refer-clojure :exclude [next rand-int])
  (:require [clojure.test.check.random :as r]))

;; Hoping to find out if JUR is efficiently splittable.
;;
;; Knuth:
;;   X_{n+k} = (a^kX_n + (a^k - 1)\frac{c}{b}) \mod m
;;
;;   where a is multiplier, b=a-1, and c is increment
;;
;;   Setting k=2:
;;
;;   X_{n+2} = (a^2X_n + (a^2 - 1)\frac{c}{b}) \mod m
;;
;;   I.e., we set the new multiplier to be (* multiplier multiplier)
;;   and the new addend to something else.
;;

(def ^:const ^:private multiplier 16r5DEECE66D)
(def ^:const ^:private addend 16rB)
(def ^:const ^:private mask (-> 1 (bit-shift-left 48) (dec)))

(defn ^:private initial-scramble
  [^long seed]
  (-> seed (bit-xor multiplier) (bit-and mask)))

(defn ^:private inc-state
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
  [rng]
  ((fn self [rng]
     (let [int (rand-int rng)
           rng2 (next rng)]
       (lazy-seq (cons int (self rng2)))))
   rng))

(comment
  (let [rng (java.util.Random. 42)]
    (repeatedly 10 #(.nextInt rng)))
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
 392236186)

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


  (./bg
    (loop [a1    multiplier
           a2    multiplier
           steps 0]
      (let [a1' (-> (unchecked-multiply a1 a1)
                    (bit-and mask))
            a2' (-> (unchecked-multiply a1 a1)
                    (as-> a
                          (unchecked-multiply a a))
                    (bit-and mask))]
        (if (= a1' a2')
          steps
          (recur a1' a2' (inc steps))))))

  )
