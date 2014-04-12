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
            [clojure.test.check.clojure-test :as ct]))

(declare shrink-loop failure)

(defn- complete
  [property num-trials seed]
  (ct/report-trial property num-trials num-trials)
  {:result true :num-tests num-trials :seed seed})

(defn not-falsey-or-exception?
  "True if the value is not falsy or an exception"
  [value]
  (and value (not (instance? Throwable value))))

(defn check-once
  ;; this can't work at all can it.
  ;;
  ;; OH WELL WE'LL FIX IT LATER
  ;;
  ;; Clean solution: change properties to create delays of results.
  [property [seed size path :as key]]
  (let [result-map-rose (gen/call-gen property (gen/random seed) size)
        result-map (loop [rose result-map-rose
                          [idx & idxs] path]
                     (if idx
                       (recur (nth (gen/rose-children rose) idx) idxs)
                       (gen/rose-root rose)))]
    (if (not-falsey-or-exception? (:result result-map))
      (complete property 1 0)
      {:result (:result result-map)
       :key key})))

;; HMMM: We could get similar functionality via a special property
;; that re-runs things and prints warnings, eh?
(defn flaky-failure-data
  "Returns a ratio indicating the failure rate, or nil if the test consistently fails."
  [property result-map]
  (let [[seed size] (:key result-map)
        _ (assert (and seed size))
        trial-count 10
        run-trial (fn []
                    (let [{:keys [result]}
                          (gen/rose-root (gen/call-gen property (gen/random seed) size))]
                      (boolean (not-falsey-or-exception? result))))
        results (repeatedly (dec trial-count) run-trial)
        failures (->> results (remove identity) (count))
        failure-rate (/ (inc failures) trial-count)]
    (when (< failure-rate 1)
      failure-rate)))

(defn quick-check
  "Tests `property` `num-tests` times.

  Examples:

      (def p (for-all [a gen/pos-int] (> (* a a) a)))
      (quick-check 100 p)
  "
  [num-tests property & {:keys [seed max-size key] :or {max-size 200}}]
  (let [meta-seed (or seed (System/currentTimeMillis))
        seed-size-seq (gen/make-seed-size-seq meta-seed max-size)]
    (if key
      (check-once property key)
      (loop [so-far 0
             seed-size-seq seed-size-seq]
        (if (== so-far num-tests)
          (complete property num-tests meta-seed)
          (let [[[seed size] & rest-seed-size-seq] seed-size-seq
                result-map-rose (gen/call-gen property (gen/random seed) size)
                result-map-rose (gen/rose-fmap-indexed
                                 (fn [path result-map]
                                   (assoc result-map :key
                                          [seed size path]))
                                 result-map-rose)
                result-map (gen/rose-root result-map-rose)
                result (:result result-map)
                args (:args result-map)]
            (if (not-falsey-or-exception? result)
              (do
                (ct/report-trial property so-far num-tests)
                (recur (inc so-far) rest-seed-size-seq))
              (if-let [data (flaky-failure-data property result-map)]
                (do
                  (println "WARNING: flaky failure detected!")
                  (println "KEY:" (:key result-map))
                  (println "INFO:" data)
                  (recur (inc so-far) rest-seed-size-seq))
                (failure property
                         result-map-rose
                         so-far
                         size)))))))))

(defn- smallest-shrink
  [total-nodes-visited depth smallest]
  {:total-nodes-visited total-nodes-visited
   :depth depth
   :result (:result smallest)
   :key (:key smallest)
   :smallest (:args smallest)})

(defonce current-shrink (atom nil))

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
  passing example was found."
  [rose-tree]
  (println "Shrinking:")
  (let [shrinks-this-depth (gen/rose-children rose-tree)]
    (loop [nodes shrinks-this-depth
           current-smallest (gen/rose-root rose-tree)
           total-nodes-visited 0
           depth 0]
      ;; instant feedback
      (reset! current-shrink current-smallest)

      (if (empty? nodes)
        (smallest-shrink total-nodes-visited depth current-smallest)
        (let [[head & tail] nodes
              result (:result (gen/rose-root head))]
          (if (not-falsey-or-exception? result)
            ;; this node passed the test, so now try testing it's right-siblings
            (do
              (print \.) (flush)
              (recur tail current-smallest (inc total-nodes-visited) depth))
            ;; this node failed the test, so check if it has children,
            ;; if so, traverse down them. If not, save this as the best example
            ;; seen now and then look at the right-siblings
            ;; children
            (do
              (print \X) (flush)
              (let [children (gen/rose-children head)]
                (if (empty? children)
                  (recur tail (gen/rose-root head) (inc total-nodes-visited) depth)
                  (recur children (gen/rose-root head) (inc total-nodes-visited) (inc depth)))))))))))

(defn- failure
  [property failing-rose-tree trial-number size]
  (let [root (gen/rose-root failing-rose-tree)
        result (:result root)
        failing-args (:args root)]
    (println "test.check test failed!" {:result result :key (:key root)})
    (ct/report-failure property result trial-number failing-args)

    {:result result
     :key (:key root)
     :failing-size size
     :num-tests (inc trial-number)
     :fail (vec failing-args)
     :shrunk (shrink-loop failing-rose-tree)}))
