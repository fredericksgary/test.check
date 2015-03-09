;   Copyright (c) Rich Hickey, Reid Draper, and contributors.
;   All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.test.check.random
  "Purely functional and splittable pseudo-random number generators based on
  http://publications.lib.chalmers.se/records/fulltext/183348/local_183348.pdf."
  (:import [clojure.test.check SipHashish]
           [java.io ByteArrayInputStream DataInputStream]
           [java.nio ByteBuffer]
           [java.security MessageDigest]
           [java.util Arrays]
           [javax.crypto Cipher]
           [javax.crypto.spec SecretKeySpec]))

(defprotocol IRandom
  (split [random]
    "Returns [rand1 rand2].

  Note: to maintain independence you should not call split and rand
  with the same argument.")
  (rand-long [random]
    "Returns a long.

  Note: to maintain independence you should not call split and rand
  with the same argument"))

(defn ^:private blowfish
  "Inputs and output are byte arrays."
  [key block]
  {:post [(= (count key) (count block) (count %))]}
  (let [c (Cipher/getInstance "Blowfish/CBC/NoPadding")
        k (SecretKeySpec. key "Blowfish")]
    (.init c Cipher/ENCRYPT_MODE k)
    (.doFinal c block)))

(defmacro ^:private def-thread-local
  [name init-expr]
  `(def ~(vary-meta name assoc :private true :tag 'ThreadLocal)
     (proxy [ThreadLocal] []
       (initialValue []
         ~init-expr))))

(def-thread-local aes-cipher-thread-local
  (Cipher/getInstance "AES/ECB/NoPadding"))

(def-thread-local byte-array-16-thread-local
  (byte-array 16))

(def-thread-local byte-buffer-8-thread-local
  (ByteBuffer/allocate 8))

(defn ^:private aes
  "Inputs and output are byte arrays."
  ([k block]
     (let [^Cipher c (.get aes-cipher-thread-local)]
       (.init c Cipher/ENCRYPT_MODE ^SecretKeySpec k)
       (.doFinal c block)))
  ([k block out]
     (let [^Cipher c (.get aes-cipher-thread-local)]
       (.init c Cipher/ENCRYPT_MODE ^SecretKeySpec k)
       (.doFinal c block 0 16 out))))

(defn ^:private siphash
  [^long k ^long in]
  (SipHashish/hashish k k in))

(defn ^:private set-bit
  "Returns a new byte array with the bit at the given index set to 1."
  [^bytes byte-array bit-index]
  (let [byte-array' (Arrays/copyOf byte-array (alength byte-array))
        byte-index (bit-shift-right bit-index 3) ;; divide by 8
        intrabyte-index (bit-and bit-index 7)
        the-byte (aget byte-array' byte-index)
        ;; some way to do this without special-casing 7?
        the-byte' (bit-or the-byte (case intrabyte-index
                                     7 -127
                                     (bit-shift-left 1 intrabyte-index)))]
    (doto byte-array'
      (aset byte-index (byte the-byte')))))

(def ^:private zero-bytes-16 (byte-array 16))

;; state and path are both length-16 byte arrays. Here we are
;; treating byte arrays as immutable so they can be shared between
;; objects (the same way that clojure's PersistentVector does).
(deftype AESRandom [state ^bytes path ^int path-length]
  IRandom
  (split [random]
    (if (= 126 path-length)
      (let [state1 (aes state path)
            state2 (aes state (set-bit path 127))]
        [(AESRandom. (SecretKeySpec. state1 "AES") zero-bytes-16 0)
         (AESRandom. (SecretKeySpec. state2 "AES") zero-bytes-16 0)])
      (let [path-length' (inc path-length)
            path2 (set-bit path path-length)]
        ;; we can reuse the existing path for the first one since the
        ;; padding bits are already set to 0
        [(AESRandom. state path path-length')
         (AESRandom. state path2 path-length')])))
  (rand-long [random]
    (let [bytes ^bytes (.get byte-array-16-thread-local)
          buffer ^ByteBuffer (.get byte-buffer-8-thread-local)]
      (aes state path bytes)
      (.getLong (doto buffer
                  (.clear)
                  (.put bytes 0 8)
                  (.flip))))))

(def ^:const highest-bit (bit-shift-left 1 63))

;; can we avoid using only a 64 bit key by keeping two of these around?
;; what does that do to perf?
(deftype SipHashRandom [^long state ^long path ^long path-length]
  IRandom
  (split [random]
    (if (= 63 path-length)
      (let [state1 (siphash state path)
            state2 (siphash state (bit-or path highest-bit))]
        [(SipHashRandom. state1 0 0)
         (SipHashRandom. state2 0 0)])
      (let [path-length' (inc path-length)
            path2 (bit-or path (bit-shift-left 1 path-length))]
        [(SipHashRandom. state path path-length')
         (SipHashRandom. state path2 path-length')])))
  (rand-long [random]
    (siphash state path)))

(deftype SHA1Random [^MessageDigest md]
  IRandom
  (split [random]
    (let [md1 ^MessageDigest (.clone md)
          md2 ^MessageDigest (.clone md)]
      (.update md1 (byte 0))
      (.update md2 (byte 1))
      [(SHA1Random. md1) (SHA1Random. md2)]))
  (rand-long [random]
    (let [md' ^MessageDigest (.clone md)
          the-bytes (.digest md')]
      (.readLong (DataInputStream. (ByteArrayInputStream. the-bytes))))))

(defn make-sha1-random
  [seed]
  (let [md (MessageDigest/getInstance "SHA1")]
    (.update md (.getBytes (pr-str seed)))
    (SHA1Random. md)))

(defn make-siphash-random
  [^long seed]
  (SipHashRandom. seed 0 0))

(defn make-aes-random
  [^long seed1 ^long seed2]
  (let [state (.array (doto (ByteBuffer/allocate 16)
                        (.putLong seed1)
                        (.putLong seed2)))]
    (->AESRandom (SecretKeySpec. state "AES") zero-bytes-16 0)))

(case (System/getenv "PRNG_IMPL")
  "AES"
  (defn make-random
    "Given zero or one or two long seeds, returns an object that can be
    used with the IRandom protocol."
    ([] (make-random (.nextLong (java.util.Random.))))
    ([^long seed] (make-aes-random seed seed))
    ([^long seed1 ^long seed2] (make-aes-random seed1 seed2)))
  (nil "siphash")
  (defn make-random
    "Given zero or one or two long seeds, returns an object that can be
    used with the IRandom protocol."
    ([] (make-random (.nextLong (java.util.Random.))))
    ([^long seed] (make-siphash-random seed))
    ([^long seed1 ^long seed2] (make-siphash-random (bit-xor seed1 seed2)))))
