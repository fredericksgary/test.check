(ns clojure.test.check.clojure-test.assertions
  #?(:cljs (:require-macros [clojure.test.check.clojure-test.assertions.cljs]))
  (:require [clojure.string :as str]
            #?(:clj [clojure.test :as t]
               :cljs [cljs.test :as t])
            [clojure.test.check.results :as results]))

#?(:clj
   (defn test-context-stacktrace [st]
     (drop-while
       #(let [class-name (.getClassName ^StackTraceElement %)]
          (or (clojure.string/starts-with? class-name "java.lang")
              (clojure.string/starts-with? class-name "clojure.test$")
              (clojure.string/starts-with? class-name "clojure.test.check.clojure_test$")
              (clojure.string/starts-with? class-name "clojure.test.check.clojure_test.assertions")))
       st)))

#?(:clj
   (defn file-and-line*
     [stacktrace]
     (if (seq stacktrace)
       (let [^StackTraceElement s (first stacktrace)]
         {:file (.getFileName s) :line (.getLineNumber s)})
       {:file nil :line nil})))

#?(:cljs
   (defn file-and-line**
     [stack-string]
     (if-let [[_ file line] (re-find #"\bat \S+ \(([^\)]+):(\d+):(\d+)\)" stack-string)]
       (doto {:file file :line line} prn)
       (do
         (prn stack-string)
         {:file "unknown" :line "unknown"}))))

(defn check-results
  [{:keys [result] :as m} & [js-error]]
  (if (results/passing? result)
    (t/do-report
      {:type :pass
       :message (dissoc m :result)})
    (t/do-report
      (merge {:type :fail
              :expected {:result true}
              :actual m}
             #?(:clj (file-and-line*
                       (test-context-stacktrace (.getStackTrace (Thread/currentThread))))
                :cljs (file-and-line** (.-stack js-error)))))))

(defn check?
  [_ form]
  `(check-results ~@(rest form)))


#?(:clj
   (defmethod t/assert-expr 'clojure.test.check.clojure-test/check?
     [_ form]
     (check? _ form)))
