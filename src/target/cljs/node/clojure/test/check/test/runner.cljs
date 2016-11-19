(ns clojure.test.check.test.runner
  (:require [cljs.nodejs :as nodejs]
            [cljs.test :as test :refer-macros [run-all-tests]]
            [clojure.test.check.test]
            [clojure.test.check.random-test]
            [clojure.test.check.rose-tree-test]
            [clojure.test.check.clojure-test-test]
            [clojure.test.check.generators :as gen]))

(nodejs/enable-util-print!)

(def results (atom nil))

(defmethod test/report [::test/default :end-run-tests]
  [m]
  (reset! results m))

(defn -main []
  (run-all-tests)
  (let [{:keys [fail error]} @results]
    (.exit js/process (+ fail error))))

(set! *main-cli-fn* -main)
