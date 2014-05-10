(ns user
  (:require [clojure.test.check.generators :as gen]
            [clojure.test.check.rose-tree :as rose]))

(def the-gen
  (gen/bind gen/nat
            (fn [n]
              (gen/fmap (fn [m] [n m]) gen/nat))))

(defn reproduced?
  [seed]
  (let [make-rose-tree #(gen/call-gen the-gen (java.util.Random. seed) 100)
        rose-1 (make-rose-tree)
        rose-2 (doto (make-rose-tree)
                 ((fn [rt1]
                    (dorun (for [rt2 (rose/children rt1)
                                 rt3 (rose/children rt2)]
                             42)))))
        last-child #(-> % rose/children last rose/root)]
    (not= (last-child rose-1)
          (last-child rose-2))))

(frequencies (map reproduced? (range 10000)))
;; => {false 9952, true 48}
