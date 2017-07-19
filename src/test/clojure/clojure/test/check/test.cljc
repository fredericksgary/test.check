;   Copyright (c) Rich Hickey, Reid Draper, and contributors.
;   All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.test.check.test
  #?(:cljs
     (:refer-clojure :exclude [infinite?]))
  (:require #?(:cljs
               [cljs.test :as test :refer-macros [deftest testing is]])
            #?(:clj
               [clojure.test :refer :all])
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen #?@(:cljs [:include-macros true])]
            [clojure.test.check.properties :as prop #?@(:cljs [:include-macros true])]
            [clojure.test.check.rose-tree :as rose]
            [clojure.test.check.random :as random]
            [clojure.test.check.results :as results]
            [clojure.test.check.clojure-test :as ct #?(:clj :refer :cljs :refer-macros) (defspec)]
            #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])))

(def gen-seed
  (let [gen-int (gen/choose 0 0x100000000)]
    (gen/fmap (fn [[s1 s2]]
                (bit-or s1 (bit-shift-left s2 32)))
              (gen/tuple gen-int gen-int))))

(defspec thomas
  (prop/for-all [x gen/nat]
    (not (<= 60 x 80))))
