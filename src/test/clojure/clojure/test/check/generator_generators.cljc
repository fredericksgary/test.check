(ns clojure.test.check.generator-generators
  "Generators that generate generators, for internal testing."
  (:require [clojure.test.check.generators :as gen #?@(:cljs [:include-macros true])]))

(def gen-integer-predicate
  (gen/recursive-gen
   (fn [inner-gen]
     (gen/one-of
      [(gen/fmap #(apply every-pred %) (gen/not-empty (gen/list inner-gen)))
       (gen/fmap #(apply some-fn %) (gen/not-empty (gen/list inner-gen)))
       (gen/fmap complement inner-gen)]))
   (gen/one-of
    [(gen/elements [(constantly true)
                    (constantly false)
                    pos?
                    neg?
                    zero?
                    even?
                    odd?])
     (gen/let [x gen/large-integer]
       #(< % x))])))

(def gen-string->integer
  (gen/elements [count
                 #(->> % (map long) (reduce (fn [^long a ^long b]
                                              (unchecked-add a b))
                                            0))
                 #(->> % (map long) (reduce (fn [^long a ^long b]
                                              (unchecked-multiply a b))
                                            1))
                 #(->> % (map long) (reduce bit-xor 0))
                 #(->> % (map long) (reduce bit-and -1))
                 #(->> % (map long) (reduce bit-or 0))]))

(def gen-string-predicate
  (gen/one-of
   [(gen/elements [(constantly true)
                   (constantly false)
                   empty?
                   not-empty])
    (gen/let [[int-p string->integer]
              (gen/tuple gen-integer-predicate gen-string->integer)]
      (comp int-p string->integer))]))

(def gen-predicate
  (gen/one-of
   [(gen/fmap #(comp % str) gen-string-predicate)
    (gen/fmap #(comp % hash) gen-integer-predicate)]))

(def gen-happy-predicate
  "Generates a predicate that usually passes."
  (gen/let [n gen/large-integer]
    (fn [x]
      (-> x hash (bit-xor n) (mod 1009) zero? not))))

(def gen-scalar-gen
  (gen/elements [(gen/return nil)
                 gen/boolean
                 gen/nat
                 gen/large-integer
                 gen/double
                 gen/uuid
                 gen/simple-type
                 gen/keyword
                 gen/keyword-ns
                 gen/symbol
                 gen/symbol-ns
                 gen/char
                 gen/char-ascii
                 gen/char-alpha
                 gen/char-alphanumeric
                 gen/string
                 gen/string-ascii
                 gen/string-alphanumeric
                 gen/ratio
                 gen/byte]))

(defn container-gen-fn
  [inner-gen-gen]
  (let [high-variety-gen-gen (gen/no-shrink
                              (gen/fmap gen/one-of
                                        (gen/vector (gen/scale #(max % 20) inner-gen-gen)
                                                    20)))
        gen-distinct-opts (gen/one-of [(gen/return {})
                                       (gen/fmap #(hash-map :num-elements %)
                                                 gen/nat)
                                       (gen/fmap #(hash-map :min-elements %)
                                                 gen/nat)
                                       (gen/fmap #(hash-map :max-elements %)
                                                 gen/nat)
                                       (gen/fmap (fn [[a b]]
                                                   {:min-elements (min a b)
                                                    :max-elements (max a b)})
                                                 (gen/tuple gen/nat gen/nat))])]
    ;; missing:
    ;; - sorted-set (hard to generate comparable keys)
    ;; - {list,vector}-distinct-by (hard to generate good key-fns)
    (gen/one-of
     [(gen/bind inner-gen-gen #(gen/fmap gen/return %))
      (gen/fmap (fn [[gen arity bounds1 bounds2 shuffle?]]
                  (cond->
                      (case (int arity)
                        1 (gen/vector gen)
                        2 (gen/vector gen bounds1)
                        3 (let [[min-length max-length] (sort [bounds1 bounds2])]
                            (gen/vector gen min-length max-length)))
                      shuffle?
                      (gen/bind gen/shuffle)))
                (gen/tuple inner-gen-gen
                           (gen/choose 1 3)
                           gen/nat
                           gen/nat
                           gen/boolean))
      (gen/fmap #(apply gen/tuple %) (gen/list inner-gen-gen))
      (gen/fmap (fn [[keys val-gens]]
                  (apply gen/hash-map (interleave keys val-gens)))
                (gen/tuple (gen/list (gen/bind inner-gen-gen identity))
                           (gen/list inner-gen-gen)))
      (gen/fmap #(gen/one-of %) (gen/not-empty (gen/list inner-gen-gen)))
      (gen/fmap (fn [[pred gen]]
                  (gen/such-that pred gen))
                (gen/tuple gen-happy-predicate
                           high-variety-gen-gen))
      (gen/fmap gen/no-shrink inner-gen-gen)
      (gen/fmap (fn [[key-gen val-gen opts]]
                  (gen/map key-gen val-gen opts ))
                (gen/tuple high-variety-gen-gen
                           inner-gen-gen
                           gen-distinct-opts))
      (gen/fmap (fn [[gen-fn gen opts]]
                  (gen-fn gen opts))
                (gen/tuple (gen/elements [gen/set gen/vector-distinct gen/list-distinct])
                           high-variety-gen-gen
                           gen-distinct-opts))])))

(def gen-gen
  "Generates a generator."
  (gen/recursive-gen container-gen-fn gen-scalar-gen))


(comment

  ;; Some code that monkeypatches the generators namespace to make
  ;; generators print as code -- useful for debugging stuff generated
  ;; by gen-gen.

  (defmethod print-method ::serializable
    [x pw]
    (-> x
        meta
        ::prints-as
        (print-method pw)))

  (defn monkeypatch-serializability!
    ([] (monkeypatch-serializability!
         "clojure.test.check.generators"))
    ([prefix]
     (let [var->sym (if prefix
                      #(symbol (str prefix)
                               (-> % meta :name str))
                      #(-> % meta :name))]
       (doseq [[sym var] (ns-publics 'clojure.test.check.generators)
               :when (not= 'generator? sym)]
         (cond
           (gen/generator? @var)
           (alter-var-root var
                           vary-meta
                           assoc
                           :type ::serializable
                           ::prints-as (var->sym var))

           (fn? @var)
           (alter-var-root var
                           (fn [orig]
                             (fn [& args]
                               (let [ret (apply orig args)]
                                 (if (gen/generator? ret)
                                   (vary-meta ret
                                              assoc
                                              :type ::serializable
                                              ::prints-as
                                              (list*
                                               (var->sym var)
                                               args))
                                   ret))))))))))

  (monkeypatch-serializability! "gen")

  )
