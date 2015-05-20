(ns clojure.test.check.compile-flags)

(def flags
  (or (some-> (System/getenv "CLOJURE_FLAGS") read-string) #{}))

(defmacro flag!
  "First pair is default."
  [& kvs]
  {:pre [(even? (count kvs))]}
  (let [pairs (partition 2 kvs)]
    (assert (every? keyword? (map first pairs)))
    (let [chosen (or (first (filter flags (map first pairs)))
                     (ffirst pairs))]
      (println "Chose:" chosen)
      ((into {} (map vec pairs)) chosen))))
