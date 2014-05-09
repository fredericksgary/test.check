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
            [clojure.test.check.rose-tree :as rose]))

(declare shrink-loop failure)

(defn not-falsey-or-exception?
  "True if the value is not falsy or an exception"
  [value]
  (and value (not (instance? Throwable value))))

(defn quick-check
  "Tests `property` `num-tests` times.

  Examples:

      (def p (for-all [a gen/pos-int] (> (* a a) a)))
      (quick-check 100 p)
  "
  [num-tests property & {:keys [seed max-size] :or {max-size 200}}]
  (let [seed (or seed (System/currentTimeMillis))
        key-seq (gen/make-key-seq seed max-size)]
    (loop [so-far 0, key-seq key-seq]
      (if (== so-far num-tests)
        :oops-passed
        (let [[key & keys] key-seq
              result-map-rose' (gen/call-key-with-meta
                               property
                               key)
              _ (def dbg result-map-rose')
              result-map-rose (rose/fmap
                               #(update-in % [:result] deref)
                               result-map-rose')
              result-map (rose/root result-map-rose)
              result (:result result-map)
              args (:args result-map)]
          (if (not-falsey-or-exception? result)
            (recur (inc so-far) keys)
            (assoc
                (failure property
                         result-map-rose
                         so-far
                         (second key))
              :result-map-rose result-map-rose')))))))

(defn- smallest-shrink
  [total-nodes-visited depth smallest]
  {:total-nodes-visited total-nodes-visited
   :depth depth
   :result (:result smallest)
   :key (:key (meta smallest))
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
  passing example was found."
  [rose-tree]
  (println "Shrinking:")
  (let [shrinks-this-depth (rose/children rose-tree)]
    (loop [nodes shrinks-this-depth
           current-smallest (rose/root rose-tree)
           total-nodes-visited 0
           depth 0]
      ;; instant feedback

      (if (empty? nodes)
        (smallest-shrink total-nodes-visited depth current-smallest)
        (let [[head & tail] nodes
              result (:result (rose/root head))]
          (if (not-falsey-or-exception? result)
            ;; this node passed the test, so now try testing its right-siblings
            (recur tail current-smallest (inc total-nodes-visited) depth)
            ;; this node failed the test, so check if it has children,
            ;; if so, traverse down them. If not, save this as the best example
            ;; seen now and then look at the right-siblings
            ;; children
            (let [children (rose/children head)]
              (if (empty? children)
                (recur tail (rose/root head) (inc total-nodes-visited) depth)
                (recur children (rose/root head) (inc total-nodes-visited) (inc depth))))))))))

(defn- failure
  [property failing-rose-tree trial-number size]
  (let [root (rose/root failing-rose-tree)
        result (:result root)
        failing-args (:args root)]
    (println "test.check test failed!" {:result result :key (:key (meta root))})

    {:result result
     :key (:key (meta root))
     :failing-size size
     :num-tests (inc trial-number)
     :fail (vec failing-args)
     :shrunk (shrink-loop failing-rose-tree)}))

;;
;; Key-backed extra functionality
;;

(defn retry
  "First arg can be a property or a defspec function."
  [prop key]
  (let [prop (-> prop meta :property (or prop))]
    @(:result (rose/root (gen/call-key-with-meta prop key)))))
