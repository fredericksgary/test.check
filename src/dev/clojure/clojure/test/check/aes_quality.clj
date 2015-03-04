(ns clojure.test.check.aes-quality
  "Debugging dieharder failures of the AES impl."
  (:require [clojure.test.check.prng-comparison :as prngc]
            [clojure.test.check.random :as r]))

;; https://code.google.com/p/dieharder/source/browse/trunk/libdieharder/diehard_bitstream.c?r=494
;;
;; Let's do the non-overlapping variant, seems easier to decompose

(def expected-mean 141909)
;; how on earth do we use this?
(def expected-sigma 290.0)

(defn stats
  [xs]
  (let [c (double (count xs))
        mean (/ (reduce + xs) c)
        variance (/ (->> xs
                         (map #(- mean %))
                         (map #(* % %))
                         (reduce +))
                    c)]
    {:mean mean
     :sigma (Math/sqrt variance)
     :variance variance}))

(defn p-value
  [xs]
  ;; haha no idea what I'm doing
  (let [{:keys [mean sigma]} (stats xs)
        standard-error (/ expected-sigma (count xs))
        z-score (/ (- mean expected-mean) standard-error)]
    ;; ???
    z-score)
  )

(defn longs->w20s
  [the-longs]
  (->> the-longs
       (mapcat (fn [the-long]
                 (->> (iterate #(bit-shift-right % 4) the-long)
                      (map #(bit-and % 15))
                      (take 16))))
       (partition 5)
       (map (fn [xs]
              (->> xs
                   (map-indexed (fn [i x]
                                  (bit-shift-left x (* 4 i))))
                   (reduce +))))))

(defonce all-w20s
  (set (range 16r100000)))

(defn diehard-bitstream
  [the-longs]
  (->> the-longs
       (longs->w20s)
       (take 16r200000)
       (set)
       (clojure.set/difference all-w20s)
       (count)))

(defn some-balanced-AES-longs
  ([] (some-balanced-AES-longs (System/currentTimeMillis)))
  ([seed]
     ((prngc/linearization-strategies :balanced-63)
      (r/make-aes-random seed seed))))

(defn some-balanced-siphash-longs
  ([] (some-balanced-siphash-longs (System/currentTimeMillis)))
  ([seed]
     ((prngc/linearization-strategies :balanced-63)
      (r/make-siphash-random seed))))

(comment

  (def AES-results (atom []))
  (./bg bg-AES
    (./forever
     (swap! AES-results conj
            (diehard-bitstream (some-balanced-AES-longs)))))

  (def siphash-results (atom []))
  (./bg bg-siphash
    (./forever
     (swap! siphash-results conj
            (diehard-bitstream (some-balanced-siphash-longs)))))


  @AES-results
  =>
  [141922
   142063
   141828
   142362
   141384
   141966
   141229
   141993
   141648
   141722
   141379
   142217
   142133
   141887
   141826
   142168
   141557
   141983
   142212
   142051
   142096
   141567
   141881
   141405
   141958
   141303
   141656
   141760
   142021
   142200
   141364
   142005
   141850
   142289
   143003
   141989
   142378
   141502
   141477
   142079
   141719
   142329
   141170
   141601
   142054
   141947
   141736
   142346
   142113
   141680
   142264
   141364
   142178
   141868
   141665
   141565
   142212
   141404
   142273
   141845
   141558
   141691
   142039
   141876
   141831
   141834
   142073
   142029
   141868
   142053
   142031
   142158
   142008
   142471
   141579
   142003
   141577
   141938
   142007
   141409
   142595
   141605
   141425
   142034
   141862
   141922
   142188
   141997
   141964
   141738
   142265
   142021
   142188
   141947
   141666
   141834
   141948
   141657
   141764
   141624
   141781
   142130
   141722
   142171
   141987
   142034
   141929
   142132
   142101
   142022
   141963
   142568
   141895
   142283
   141689
   142055
   142295
   141653
   141542
   141617
   141733
   142129
   141797
   142237]

  @siphash-results
  =>
  [141649
   142029
   141746
   142583
   141940
   141461
   141996
   141717
   141535
   142162
   141825
   141337
   142442
   142122
   141762
   142032
   141939
   142085
   141746
   141738
   141375
   141543
   141450
   141230
   141316
   142022
   142061
   140982
   142255
   142101
   141600
   141951
   142427
   142506
   141809
   142164
   142151
   142179
   141769
   142158
   142091
   142082
   141934
   141651
   141778
   142138
   141587
   142203
   142123
   141930
   141811
   142259
   141737
   142128
   141848
   142270
   141707
   141530
   141546
   141841
   142017
   141624
   141715
   141724
   141420
   142132
   142021
   142236
   142243
   141658
   141961
   141768
   142045
   141615
   141533
   141879
   141859
   141882
   141621
   141945
   141887
   141777
   142003
   142264
   142005
   141809
   142296
   141688
   142200
   141772
   142096
   142323
   142831
   141667
   141742
   142053
   141736
   141634
   141451
   142607
   141327
   142337
   141815
   142277
   141903
   141972
   142019
   141785
   141520
   142132
   142205
   141410
   141545
   142519
   141770
   142197
   142577
   142035
   142512
   141707
   141743
   141416
   141874
   142373
   142047
   142143
   142025]
  )
