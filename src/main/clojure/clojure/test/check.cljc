;   Copyright (c) Rich Hickey, Reid Draper, and contributors.
;   All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.test.check
  (:require [clojure.test.check.generators :as gen]
            [clojure.test.check.random :as random]
            [clojure.test.check.rose-tree :as rose]
            [clojure.test.check.impl :refer [get-current-time-millis
                                             exception-like?]])
  (:import (java.util.concurrent TimeUnit ArrayBlockingQueue)))

(declare shrink-loop failure)

(defn- make-rng
  [seed]
  (if seed
    [seed (random/make-random seed)]
    (let [non-nil-seed (get-current-time-millis)]
      [non-nil-seed (random/make-random non-nil-seed)])))

(defn- complete
  [property num-trials seed reporter-fn]
  (reporter-fn {:type :complete
                :property property
                :result true
                :num-tests num-trials
                :seed seed})

  {:result true :num-tests num-trials :seed seed})

(defn- not-falsey-or-exception?
  "True if the value is not falsy or an exception"
  [value]
  (and value (not (exception-like? value))))

(defn quick-check
  "Tests `property` `num-tests` times.

  Takes several optional keys:

  `:seed`
    Can be used to re-run previous tests, as the seed used is returned
    after a test is run.

  `:max-size`.
    can be used to control the 'size' of generated values. The size will
    start at 0, and grow up to max-size, as the number of tests increases.
    Generators will use the size parameter to bound their growth. This
    prevents, for example, generating a five-thousand element vector on
    the very first test.

  `:reporter-fn`
    A callback function that will be called at various points in the test
    run, with a map like:

      ;; called after a passing trial
      {:type      :trial
       :property  #<...>
       :so-far    <number of tests run so far>
       :num-tests <total number of tests>}

      ;; called after each failing trial
      {:type         :failure
       :property     #<...>
       :result       ...
       :trial-number <tests ran before failure found>
       :failing-args [...]}

    It will also be called on :complete, :shrink-step and :shrunk.

  Examples:

      (def p (for-all [a gen/pos-int] (> (* a a) a)))

      (quick-check 100 p)
      (quick-check 200 p
                   :seed 42
                   :max-size 50
                   :reporter-fn (fn [m]
                                  (when (= :failure (:type m))
                                    (println \"Uh oh...\"))))"
  [num-tests property & {:keys [seed max-size reporter-fn nthreads]
                         :or {max-size 200, reporter-fn (constantly nil)
                              nthreads 1}}]
  ;; should there be an option to use the number of CPUs available?
  ;; or a function thereof? :/
  (let [[created-seed rng] (make-rng seed)
        test-args (fn [] (take num-tests
                               (map vector
                                    (rest (range))
                                    (gen/make-size-range-seq max-size)
                                    ((fn f [r]
                                       (lazy-seq
                                        (let [[r1 r2] (random/split r)]
                                          (cons r1 (f r2)))))
                                     rng))))]
    (if (= 1 nthreads)
      (or (reduce (fn [_ [so-far size rng]]
                    (let [result-map-rose (gen/call-gen property rng size)
                          result-map (rose/root result-map-rose)
                          result (:result result-map)]
                      (if (not-falsey-or-exception? result)
                        (do
                          (reporter-fn {:type :trial
                                        :property property
                                        :so-far so-far
                                        :num-tests num-tests})
                          nil)
                        (reduced
                         (failure property result-map-rose so-far size created-seed reporter-fn)))))
                  nil
                  (test-args))
          (complete property num-tests created-seed reporter-fn))
      (let [test-args-queue (ArrayBlockingQueue. 100)
            results-queue (ArrayBlockingQueue. 100)
            done? (atom false)]
        (try
          (future
            (loop [[test-arg & more :as test-args] (test-args)]
              (when (and test-arg (not @done?))
                (if (.offer test-args-queue test-arg 1 TimeUnit/MILLISECONDS)
                  (recur more)
                  (recur test-args)))))
          ;; how do we pool the threads without preventing concurrent
          ;; calls of quick-check or successive calls with different
          ;; values of :nthreads?
          (dotimes [_ nthreads]
            (.start (Thread. (bound-fn []
                               (when-not @done?
                                 ;; TODO: um we need error handling
                                 ;; here too for when generators
                                 ;; throw exceptions
                                 (when-let [[so-far size rng :as test-arg]
                                            (.poll test-args-queue 1 TimeUnit/MILLISECONDS)]
                                   (let [result-map-rose (gen/call-gen property rng size)
                                         ret (conj test-arg result-map-rose)]
                                     (loop []
                                       (or (.offer results-queue ret 1 TimeUnit/MILLISECONDS)
                                           (and (not @done?)
                                                (recur))))))
                                 (recur))))))

          (loop [max-test-num-seen 0
                 missing-test-nums (sorted-set)
                 so-far 1
                 maybe-first-failing-result nil]
            (if (and (>= max-test-num-seen num-tests) (empty? missing-test-nums))
              (complete property num-tests created-seed reporter-fn)
              (if-let [[test-num size rng result-map-rose :as result]
                       (.poll results-queue 1 TimeUnit/MILLISECONDS)]
                (let [missing-test-nums'
                      (-> missing-test-nums
                          (disj test-num)
                          (into (range (inc max-test-num-seen) test-num)))]
                  (if (not-falsey-or-exception? (:result (rose/root result-map-rose)))
                    (do
                      (reporter-fn {:type :trial
                                    :property property
                                    :so-far so-far
                                    :num-tests num-tests})
                      (if (and maybe-first-failing-result
                               (empty? (subseq missing-test-nums' < test-num)))
                        (let [[test-num size rng result-map-rose] maybe-first-failing-result]
                          (failure property result-map-rose test-num size created-seed reporter-fn))
                        (recur (max max-test-num-seen test-num)
                               missing-test-nums'
                               (inc so-far)
                               maybe-first-failing-result)))
                    (do
                      (reset! done? :failure)
                      (if (and maybe-first-failing-result
                               (< (first maybe-first-failing-result) test-num))
                        (recur (max max-test-num-seen test-num)
                               missing-test-nums'
                               (inc so-far)
                               maybe-first-failing-result)
                        (if (empty? (subseq missing-test-nums' < test-num))
                          (failure property result-map-rose so-far size created-seed reporter-fn)
                          (recur (max max-test-num-seen test-num)
                                 missing-test-nums'
                                 (inc so-far)
                                 result))))))
                (recur max-test-num-seen missing-test-nums so-far maybe-first-failing-result))))
          (finally
            (reset! done? :finally)))))))

(defn- smallest-shrink
  [total-nodes-visited depth smallest]
  {:total-nodes-visited total-nodes-visited
   :depth depth
   :result (:result smallest)
   :smallest (:args smallest)})

(defn- shrink-loop
  "Shrinking a value produces a sequence of smaller values of the same type.
  Each of these values can then be shrunk. Think of this as a tree. We do a
  modified depth-first search of the tree:

  Do a non-exhaustive search for a deeper (than the root) failing example.
  Additional rules added to depth-first search:
  * If a node passes the property, you may continue searching at this depth,
  but not backtrack
  * If a node fails the property, search its children
  The value returned is the left-most failing example at the depth where a
  passing example was found.

  Calls reporter-fn on every shrink step."
  [rose-tree reporter-fn]
  (let [shrinks-this-depth (rose/children rose-tree)]
    (loop [nodes shrinks-this-depth
           current-smallest (rose/root rose-tree)
           total-nodes-visited 0
           depth 0]
      (if (empty? nodes)
        (smallest-shrink total-nodes-visited depth current-smallest)
        (let [;; can't destructure here because that could force
              ;; evaluation of (second nodes)
              head (first nodes)
              tail (rest nodes)
              result (:result (rose/root head))
              args (:args (rose/root head))
              shrink-step-map {:type :shrink-step
                               :result result
                               :args args}]
          (if (not-falsey-or-exception? result)
            ;; this node passed the test, so now try testing its right-siblings
            (do
              (reporter-fn (merge shrink-step-map {:pass? true
                                                   :current-smallest current-smallest}))
              (recur tail current-smallest (inc total-nodes-visited) depth))
            ;; this node failed the test, so check if it has children,
            ;; if so, traverse down them. If not, save this as the best example
            ;; seen now and then look at the right-siblings
            ;; children
            (let [new-smallest (rose/root head)]
              (reporter-fn (merge shrink-step-map {:pass? false
                                                   :current-smallest new-smallest}))
              (if-let [children (seq (rose/children head))]
                (recur children new-smallest (inc total-nodes-visited) (inc depth))
                (recur tail new-smallest (inc total-nodes-visited) depth)))))))))

(defn- failure
  [property failing-rose-tree trial-number size seed reporter-fn]
  (let [root (rose/root failing-rose-tree)
        result (:result root)
        failing-args (:args root)]

    (reporter-fn {:type :failure
                  :property property
                  :result result
                  :trial-number trial-number
                  :failing-args failing-args})

    (let [shrunk (shrink-loop failing-rose-tree
                              #(reporter-fn (assoc % :property property)))]
      (reporter-fn {:type :shrunk
                    :property property
                    :trial-number trial-number
                    :failing-args failing-args
                    :shrunk shrunk})
      {:result result
       :seed seed
       :failing-size size
       :num-tests trial-number
       :fail (vec failing-args)
       :shrunk shrunk})))
