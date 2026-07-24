(ns nrepl.transport-test
  "The nREPL framing adapter above teensyp.client."
  (:require [clojure.test :refer [deftest is testing]]
            [nrepl.bencode :as bencode]
            [nrepl.transport :as transport]
            [teensyp.client :as client]))

(defn- wire-bytes [wire]
  (byte-array (map int wire)))

(defn- bytes->wire [bytes]
  (String. bytes "ISO-8859-1"))

(defn- fake-transport []
  {:connection :fake-connection
   :buf (atom "")})

(defn- next-chunk! [chunks]
  (let [chunk (first @chunks)]
    (swap! chunks #(vec (next %)))
    chunk))

(deftest connect-delegates-with-one-bounded-deadline
  (let [call (atom nil)]
    (with-redefs [client/connect
                  (fn [host port opts]
                    (reset! call [host port opts])
                    :opaque-connection)]
      (let [t (transport/connect "nrepl.example" 7888)]
        (is (= ["nrepl.example" 7888 {:connect-timeout-ms 30000}]
               @call))
        (is (= :opaque-connection (:connection t)))
        (is (not (contains? t :fd)))
        (is (not (contains? t :socket)))))))

(deftest recv-accumulates-partial-message-bytes
  (let [message {"id" "utf8"
                 "value" "naïve ☃"}
        wire (bencode/encode message)
        chunks (atom [(wire-bytes (subs wire 0 3))
                      (wire-bytes (subs wire 3 11))
                      (wire-bytes (subs wire 11))])]
    (with-redefs [client/receive-at-most!
                  (fn [connection max-bytes]
                    (is (= :fake-connection connection))
                    (is (= 65536 max-bytes))
                    (next-chunk! chunks))]
      (is (= message (transport/recv (fake-transport))))
      (is (empty? @chunks)))))

(deftest recv-preserves-multiple-messages-from-one-chunk
  (let [first-message {"id" "one" "value" "first"}
        second-message {"id" "two" "value" "second"}
        chunk (wire-bytes
                (str (bencode/encode first-message)
                     (bencode/encode second-message)))
        calls (atom 0)
        t (fake-transport)]
    (with-redefs [client/receive-at-most!
                  (fn [_ _]
                    (swap! calls inc)
                    chunk)]
      (is (= first-message (transport/recv t)))
      (is (= second-message (transport/recv t)))
      (is (= 1 @calls)))))

(deftest recv-returns-nil-at-eof
  (testing "clean EOF with an empty framing buffer"
    (with-redefs [client/receive-at-most! (fn [_ _] nil)]
      (is (nil? (transport/recv (fake-transport))))))

  (testing "EOF does not invent a message from a partial frame"
    (let [chunks (atom [(wire-bytes "d2:id3:cut") nil])]
      (with-redefs [client/receive-at-most!
                    (fn [_ _] (next-chunk! chunks))]
        (is (nil? (transport/recv (fake-transport))))
        (is (empty? @chunks))))))

(deftest send-passes-one-whole-message-per-client-call
  (let [start (promise)
        calls (atom [])
        t (fake-transport)
        messages [{:op "eval" :id "a" :code "(+ 1 2)"}
                  {:op "describe" :id "b"}]]
    ;; The adapter makes exactly one send-all! call per bencode value. The
    ;; client's write-direction gate serializes these calls on a real
    ;; connection, so concurrent senders cannot interleave message bytes.
    (with-redefs [client/send-all!
                  (fn [connection bytes]
                    (is (= :fake-connection connection))
                    (swap! calls conj bytes)
                    nil)]
      (let [a (future @start (transport/send t (first messages)))
            b (future @start (transport/send t (second messages)))]
        (deliver start true)
        (is (nil? (deref a 2000 ::timeout)))
        (is (nil? (deref b 2000 ::timeout)))))
    (is (= 2 (count @calls)))
    (is (= (set (map #(into {} (map (fn [[k v]] [(name k) v]) %))
                     messages))
           (set (map #(first (bencode/decode (bytes->wire %))) @calls))))))

(deftest transport-does-not-rewrite-client-errors
  (let [native-error (ex-info "connection reset"
                              {:jolt.net/op :read
                               :jolt.net/code 104})]
    (with-redefs [client/receive-at-most!
                  (fn [_ _] (throw native-error))]
      (is (identical?
            native-error
            (try
              (transport/recv (fake-transport))
              nil
              (catch :default e e)))))))

(deftest close-delegates-and-retains-nil-result
  (let [closed (atom nil)]
    (with-redefs [client/close!
                  (fn [connection]
                    (reset! closed connection)
                    true)]
      (is (nil? (transport/close (fake-transport))))
      (is (= :fake-connection @closed)))))
