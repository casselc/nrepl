(ns nrepl.bencode-test
  "Compatibility and byte-native nREPL bencode facade tests."
  (:require [clojure.test :refer [deftest is are testing]]
            [jolt.bytes :as bytes]
            [nrepl.bencode :as bencode]))

(defn- roundtrip [v] (first (bencode/decode (bencode/encode v))))

(deftest integer-roundtrip
  (are [n] (= n (roundtrip n))
    0 1 -1 42 1234567890))

(deftest string-roundtrip
  (are [s] (= s (roundtrip s))
    "" "a" "hello" "spaces and stuff" "with:colons:and1234numbers"))

(deftest unicode-string-roundtrip
  (is (= "naïve ☃ ßÒ" (roundtrip "naïve ☃ ßÒ"))))

(deftest list-roundtrip
  (is (= [] (roundtrip [])))
  (is (= ["a" "b"] (roundtrip ["a" "b"])))
  (is (= [1 2 3] (roundtrip [1 2 3]))))

(deftest map-roundtrip
  (is (= {"a" "b"} (roundtrip {"a" "b"})))
  (is (= {"op" "eval" "id" "7" "code" "(+ 1 2)"}
         (roundtrip {"op" "eval" "id" "7" "code" "(+ 1 2)"})))
  (is (= {"status" ["done"]} (roundtrip {"status" ["done"]}))))

(deftest partial-decode-returns-nil
  (testing "a buffer without a complete value decodes to nil"
    (is (nil? (bencode/decode "5:ab")))      ;; string length 5, only 2 bytes
    (is (nil? (bencode/decode "i42")))       ;; integer without terminator
    (is (nil? (bencode/decode "d2:op"))))    ;; dict missing the value
  (testing "an index beyond accumulated input retains the historical nil"
    (is (nil? (bencode/decode "" 0)))
    (is (nil? (bencode/decode "1:a" 3)))
    (is (nil? (bencode/decode "1:a" 9)))))

(deftest decode-returns-next-index
  (let [s (str (bencode/encode {"a" 1}) (bencode/encode {"b" 2}))
        [m1 i] (bencode/decode s 0)
        [m2 _] (bencode/decode s i)]
    (is (= {"a" 1} m1))
    (is (= {"b" 2} m2))))

(deftest byte-native-api-distinguishes-all-three-results
  (let [wire (bencode/encode-bytes {"op" "describe"})
        success (bencode/decode-bytes wire)
        partial-array (byte-array (take (dec (alength wire)) wire))
        partial-cursor
        (bytes/cursor (bytes/window partial-array))
        partial (bencode/decode-cursor partial-cursor)
        invalid-cursor
        (bytes/cursor
         (bytes/window (.getBytes "i03e" "ISO-8859-1")))
        invalid (bencode/decode-cursor invalid-cursor)]
    (is (bytes? wire))
    (is (= :ok (:status success)))
    (is (= {"op" "describe"} (:value success)))
    (is (= :need-more (:status partial)))
    (is (identical? partial-cursor (:cursor partial)))
    (is (= :invalid (:status invalid)))
    (is (= :noncanonical-integer (:reason invalid)))
    (is (identical? invalid-cursor (:cursor invalid)))))

(deftest malformed-compatibility-input-fails-instead-of-accumulating
  (let [error
        (try
          (bencode/decode "i03e")
          nil
          (catch Throwable error error))]
    (is (= :invalid-frame
           (:nrepl.bencode/error (ex-data error))))
    (is (= :noncanonical-integer
           (:reason (ex-data error))))))
