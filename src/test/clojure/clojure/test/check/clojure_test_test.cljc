;   Copyright (c) Rich Hickey, Reid Draper, and contributors.
;   All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.test.check.clojure-test-test
  #?(:clj (:use clojure.test))
  (:require #?@(:cljs
                [[cljs.test :as test :refer [test-var] :refer-macros [is]]
                 [cljs.reader :refer [read-string]]])
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop #?@(:cljs [:include-macros true])]
            [clojure.test.check.clojure-test :as ct #?@(:clj  [:refer (defspec)]
                                                        :cljs [:refer-macros (defspec)])]))

(defspec default-trial-counts
  (prop/for-all* [gen/int] (constantly true)))

(defspec trial-counts 5000
  (prop/for-all* [gen/int] (constantly true)))

;; NOTE: No Thread/sleep in JS, so no :cljs version - David
#?(:clj
    (defspec long-running-spec 1000
      (prop/for-all* [] #(do (Thread/sleep 1) true))))

(defn- vector-elements-are-unique*
  [v]
  (== (count v) (count (distinct v))))

(def ^:private vector-elements-are-unique
  (prop/for-all*
    [(gen/vector gen/int)]
    vector-elements-are-unique*))

(defspec this-is-supposed-to-fail 100 vector-elements-are-unique)

(defn- capture-test-var
  [v]
  (doto (with-out-str #?(:clj  (binding [*test-out* *out*] (test-var v))
                         :cljs (test-var v)))
    println))

(defn test-ns-hook
  []
  (is (-> (capture-test-var #'default-trial-counts)
          read-string
          :num-tests
          (= ct/*default-test-count*)))

  (is (-> (capture-test-var #'trial-counts)
          read-string
          (select-keys [:test-var :result :num-tests])
          (= {:test-var "trial-counts", :result true, :num-tests 5000})))

  (binding [ct/*report-trials* true]
     (let [output (capture-test-var #'trial-counts)]
       (is (re-matches #?(:clj  #"(?s)\.{5}.+"
                          :cljs #"\.{5}[\s\S]+")
                       output))))

  ;; NOTE: No Thread/sleep in JS - David
  #?(:clj
      (binding [ct/*report-trials* ct/trial-report-periodic
                ct/*trial-report-period* 500]
        (is (re-seq
              #"(Passing trial \d{3} / 1000 for .+\n)+"
              (capture-test-var #'long-running-spec)))))

  (let [[report-counters stdout]
        #?(:clj
           (binding [ct/*report-shrinking* true
                      ; need to keep the failure of this-is-supposed-to-fail from
                      ; affecting the clojure.test.check test run
                      *report-counters* (ref *initial-report-counters*)]
             (let [out (capture-test-var #'this-is-supposed-to-fail)]
               [@*report-counters* out]))

           :cljs
           (binding [ct/*report-shrinking* true]
             ;; need to keep the failure of this-is-supposed-to-fail from
             ;; affecting the clojure.test.check test run
             (let [restore-env    (test/get-current-env)
                   _              (test/set-env! (test/empty-env))
                   report-str     (capture-test-var #'this-is-supposed-to-fail)
                   env            (test/get-current-env)]
               (test/set-env! restore-env)
               [(:report-counters env) report-str])))]
    (is (== 1 (:fail report-counters)))
    (is (re-seq
          #?(:clj
             #"(?s)Shrinking vector-elements-are-unique starting with parameters \[\[.+"

             :cljs
             #"Shrinking vector-elements-are-unique starting with parameters \[\[[\s\S]+")
          stdout))))
