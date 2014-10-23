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
            [clojure.test.check.clojure-test :as ct]
            [clojure.test.check.rose-tree :as rose]))

(declare shrink-loop failure)

(defn- check-interrupts
  [msg]
  (when (Thread/interrupted)
    (throw (InterruptedException. msg))))

(defn- complete
  [property num-trials seed]
  (ct/report-trial property num-trials num-trials)
  {:result true :num-tests num-trials :seed seed})

(defn not-falsey-or-exception?
  "True if the value is not falsy or an exception"
  [value]
  (and value (not (instance? Throwable value))))

(defn quick-check
  "Tests `property` `num-tests` times.
  Takes optional keys `:seed` and `:max-size`. The seed parameter
  can be used to re-run previous tests, as the seed used is returned
  after a test is run. The max-size can be used to control the 'size'
  of generated values. The size will start at 0, and grow up to
  max-size, as the number of tests increases. Generators will use
  the size parameter to bound their growth. This prevents, for example,
  generating a five-thousand element vector on the very first test.

  Examples:

      (def p (for-all [a gen/pos-int] (> (* a a) a)))
      (quick-check 100 p)
  "
  [num-tests property & {:keys [seed max-size] :or {max-size 200}}]
  (let [seed (or seed (System/currentTimeMillis))
        key-seq (gen/make-key-seq seed max-size)]
    (loop [so-far 0, key-seq key-seq]
      (check-interrupts "quick-check interrupted!")
      (if (== so-far num-tests)
        (complete property num-tests seed)
        (let [[key & keys] key-seq
              result-map-rose (gen/call-key-with-meta
                               property
                               key)
              result-map-rose (rose/fmap
                               #(update-in % [:result] deref)
                               result-map-rose)
              result-map (rose/root result-map-rose)
              result (:result result-map)
              args (:args result-map)]
          (if (not-falsey-or-exception? result)
            (do
              (ct/report-trial property so-far num-tests)
              (recur (inc so-far) keys))
            (failure property
                     result-map-rose
                     so-far
                     (second key))))))))

(defn- smallest-shrink
  [total-nodes-visited depth smallest]
  {:total-nodes-visited total-nodes-visited
   :depth (-> smallest meta :key (get 2) count)
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

      (check-interrupts "Shrink interrupted!")

      (if (empty? nodes)
        (smallest-shrink total-nodes-visited current-smallest)
        (let [[head & tail] nodes
              result (:result (rose/root head))]
          (if (not-falsey-or-exception? result)
            ;; this node passed the test, so now try testing its right-siblings
            (do
              (print \.) (flush)
              (recur tail current-smallest (inc total-nodes-visited) depth))
            ;; this node failed the test, so check if it has children,
            ;; if so, traverse down them. If not, save this as the best example
            ;; seen now and then look at the right-siblings
            ;; children
            (do
              (print \newline)
              (println "Smaller:" (-> head rose/root meta :key))
              (flush)
              (let [children (rose/children head)]
                (if (empty? children)
                  (recur tail (rose/root head) (inc total-nodes-visited) depth)
                  (recur children (rose/root head) (inc total-nodes-visited) (inc depth)))))))))))

(defn- failure
  [property failing-rose-tree trial-number size]
  (let [root (rose/root failing-rose-tree)
        result (:result root)
        failing-args (:args root)]
    (printf "test.check test failed! (%s)\n" (print-str property))
    (prn {:result result :key (:key (meta root))})
    (ct/report-failure property result trial-number failing-args)

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
  (let [prop (-> prop meta :property (or prop))
        {:keys [result args]} (rose/root (gen/call-key-with-meta prop key))]
    (if (not-falsey-or-exception? @result)
      {:result @result}
      {:fail args, :result @result})))

(defn resume-shrink
  "First arg can be a property or a defspec function."
  [prop key]
  (let [prop (-> prop meta :property (or prop))
        result-map-rose (rose/fmap
                         #(update-in % [:result] deref)
                         (gen/call-key-with-meta prop key))]
    (shrink-loop result-map-rose)))

;;
;; Hyper Shrinking!
;;

(defn ^:private quiet-nth
  "Like nth but returns nil if i is out of range."
  [coll i]
  (cond (zero? i) (first coll)
        (empty? coll) nil
        :else (recur (rest coll) (dec i))))

(defn- hyper-shrink-loop
  [rose-tree]
  (println "Shrinking:")
  (letfn [(vanilla [nodes current-smallest total-nodes-visited]
            (println "vanilla")
            (if (empty? nodes)
              (smallest-shrink total-nodes-visited current-smallest)
              (let [[head & tail] nodes
                    result (force (:result (rose/root head)))]
                (if (not-falsey-or-exception? result)
                  ;; this node passed the test, so now try testing its right-siblings
                  (do
                    (print \.) (flush)
                    (recur tail current-smallest (inc total-nodes-visited)))
                  ;; this node failed the test, so check if it has children,
                  ;; if so, traverse down them. If not, save this as the best example
                  ;; seen now and then look at the right-siblings
                  ;; children
                  (do
                    (print \newline)
                    (println "Smaller:" (-> head rose/root meta :key))
                    (flush)
                    (let [children (rose/children head)]
                      (if (empty? children)
                        (recur tail (rose/root head) (inc total-nodes-visited))
                        #(hyper-start head (inc total-nodes-visited)))))))))
          (hyper-start [rose-tree total-nodes-visited]
            (println "hyper-start")
            (let [last-index (-> rose-tree rose/root meta :key (get 2) peek)]
              #(hyper-unbounded rose-tree
                                total-nodes-visited
                                last-index
                                1)))
          (hyper-unbounded [rose-tree total-nodes-visited i next-jump]
            (println "hyper-unbounded" i next-jump)
            (hyper-unbounded* (-> rose-tree rose/root meta ::reproducer)
                              (nth (iterate #(quiet-nth (rose/children %) i) rose-tree) next-jump)
                              total-nodes-visited i next-jump))
          (hyper-unbounded* [rose-tree-ghost rose-tree' total-nodes-visited i next-jump]
            (if (and rose-tree'
                     (-> rose-tree' rose/root :result force
                         not-falsey-or-exception? not))
              (do
                (println "\nSmaller:" (-> rose-tree' rose/root meta :key))
                #(hyper-unbounded rose-tree' (inc total-nodes-visited) i (* 2 next-jump)))
              (do
                (print \.) (flush)
                #(hyper-bounded (rose-tree-ghost) (inc total-nodes-visited) i next-jump))) )

          (hyper-bounded [rose-tree total-nodes-visited i too-high]
            (println "hyper-bounded" i too-high)
            (if (<= too-high 1)
              #(vanilla (rose/children rose-tree)
                        (rose/root rose-tree)
                        total-nodes-visited)
              ;; binary search
              (let [jump (quot too-high 2)
                    rose-tree-ghost (-> rose-tree rose/root meta ::reproducer)
                    rose-tree' (nth (iterate #(quiet-nth (rose/children %) i) rose-tree) jump)]
                (if (and rose-tree'
                         (-> rose-tree' rose/root :result force
                             not-falsey-or-exception? not))
                  (do
                    (println "\nSmaller:" (-> rose-tree' rose/root meta :key))
                    (recur rose-tree' (inc total-nodes-visited) i (- too-high jump)))
                  (do
                    (print \.) (flush)
                    (recur (rose-tree-ghost) (inc total-nodes-visited) i jump))))))]
    (trampoline vanilla (rose/children rose-tree) (rose/root rose-tree) 0)))

(declare reproducibilize)

(defn reproduce
  [prop key]
  (println "REPRODUCING")
  (reproducibilize (gen/call-key-with-meta prop key)))

(defn reproducibilize
  [rose-tree prop]
  (rose/fmap (fn [x]
               (let [{:keys [key]} (meta x)]
                 (vary-meta x assoc ::reproducer (partial reproduce prop key))))
             rose-tree))

(defn debuggify
  [rose-tree]
  (rose/fmap (fn [m]
               (let [k (-> m meta :key)]
                 (update-in m [:result] (fn [d]
                                          (delay (println "  Running test with" k)
                                                 (force d))))))
             rose-tree))

(defn hyper-shrink
  "Like resume-shrink, but uses an aggressive shrinking algorithm that
  tries to take advantage of shrinks that take many consecutive steps
  down the same index'd child."
  [prop key]
  (let [prop (-> prop meta :property (or prop))
        rose-tree (reproducibilize (gen/call-key-with-meta prop key) prop)]
    (hyper-shrink-loop (debuggify rose-tree))))
