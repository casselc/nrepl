(ns nrepl.transport-hegel-runner
  (:require [hegel.core :as h]
            [hegel.generator :as g]
            [jolt.bytes :as bytes]
            [nrepl.bencode :as bencode]
            [nrepl.transport :as transport]
            [teensyp.client :as client]))

(defn- fail! [origin message data]
  (throw (ex-info message (assoc data :hegel/origin origin))))

(defn- require-passed! [label result]
  (when-not (and (:passed? result) (not (:flaky? result)))
    (throw
     (ex-info "nREPL transport Hegel property failed"
              {:label label :result result})))
  result)

(defn- byte-array-of [octets]
  (byte-array (mapv unchecked-byte octets)))

(defn- append-bytes [left right]
  (let [result (byte-array (+ (alength left) (alength right)))]
    (System/arraycopy left 0 result 0 (alength left))
    (System/arraycopy right 0 result (alength left) (alength right))
    result))

(defn- encode-messages [messages]
  (reduce
   append-bytes
   (byte-array 0)
   (map bencode/encode-bytes messages)))

(defn- fake-transport [max-frame-bytes]
  {:connection :fake-connection
   :buf (atom (bytes/cursor (bytes/window (byte-array 0))))
   :max-frame-bytes max-frame-bytes})

(defn- next-chunk! [chunks calls]
  (swap! calls inc)
  (let [remaining @chunks]
    (if (seq remaining)
      (let [chunk (first remaining)]
        (reset! chunks (vec (next remaining)))
        chunk)
      nil)))

(def ^:private message-keys
  ["id" "op" "session" "value" "status" "out" "err" "ns"])

(defn- message-generator []
  (g/map
   {:min-size 1 :max-size 6}
   (g/sampled-from message-keys)
   (g/one-of
    [(g/integer -1000000 1000000)
     (g/sampled-from
      ["" "a" "eval" "done" "naïve" "☃" "(+ 1 2)"])])))

(defn- chunk-regrouping-property! []
  (require-passed!
   :chunk-regrouping
   (h/run-test!
    {:test-cases 400
     :seed 20260731
     :name "nrepl.transport/arbitrary-chunks-and-concatenated-frames/v1"
     :database ""
     :verbosity :quiet}
    (fn [_]
      (g/let [messages
              (g/vector
               {:min-size 1 :max-size 4}
               (message-generator))
              chunks
              (g/chunkings
               (mapv #(bit-and (long %) 0xff)
                     (seq (encode-messages messages))))]
        (let [pending (atom (mapv byte-array-of chunks))
              calls (atom 0)
              receiver (fake-transport 67108864)]
          (with-redefs
            [client/receive-at-most!
             (fn [connection max-bytes]
               (when-not (= [:fake-connection 65536]
                            [connection max-bytes])
                 (fail! "nrepl.transport/chunks:client-call"
                        "transport changed its bounded client receive call"
                        {:connection connection
                         :max-bytes max-bytes}))
               (next-chunk! pending calls))]
            (let [actual
                  (mapv (fn [_] (transport/recv receiver)) messages)
                  eof (transport/recv receiver)
                  checks
                  [(nil? eof)
                   (empty? @pending)
                   (zero? (bytes/remaining @(:buf receiver)))
                   (= (inc (count chunks)) @calls)]]
              (when-not (= messages actual)
                (fail! "nrepl.transport/chunks:messages"
                       "socket chunk regrouping changed message boundaries"
                       {:expected messages
                        :actual actual
                        :chunks chunks}))
              (when-not (every? true? checks)
                (fail! "nrepl.transport/chunks:clean-eof"
                       "transport did not finish at an exact empty boundary"
                       {:eof eof
                        :checks checks
                        :pending (count @pending)
                        :remaining
                        (bytes/remaining @(:buf receiver))
                        :calls @calls
                        :expected-calls (inc (count chunks))}))))))))))

(defn- truncated-eof-property! []
  (require-passed!
   :truncated-eof
   (h/run-test!
    {:test-cases 300
     :seed 20260801
     :name "nrepl.transport/proper-prefix-eof-fails-closed/v1"
     :database ""
     :verbosity :quiet}
    (fn [_]
      (g/let [message (message-generator)]
        (let [wire (bencode/encode-bytes message)
              cut (h/draw! (g/integer 1 (dec (alength wire))))
              prefix
              (mapv #(bit-and (long %) 0xff)
                    (take cut (seq wire)))
              chunks (h/draw! (g/chunkings prefix))
              pending (atom (mapv byte-array-of chunks))
              calls (atom 0)
              receiver (fake-transport 67108864)
              error
              (with-redefs
                [client/receive-at-most!
                 (fn [_ _] (next-chunk! pending calls))]
                (try
                  (transport/recv receiver)
                  nil
                  (catch Throwable error
                    error)))
              valid?
              (and
               (= :truncated-frame
                  (:nrepl.transport/error (ex-data error)))
               (= cut (:unread (ex-data error)))
               (empty? @pending)
               (= (inc (count chunks)) @calls))]
          (when-not valid?
            (fail! "nrepl.transport/eof:truncated"
                   "proper frame prefix was accepted as clean EOF"
                   {:message message
                    :cut cut
                    :chunks chunks
                    :calls @calls
                    :error (some-> error ex-data)}))))))))

(defn- frame-limit-property! []
  (require-passed!
   :frame-limit
   (h/run-test!
    {:test-cases 300
     :seed 20260802
     :name "nrepl.transport/frame-limit-before-allocation/v1"
     :database ""
     :verbosity :quiet}
    (fn [_]
      (g/let [limit (g/integer 0 256)
              octets (g/vector {:size (inc limit)} (g/octet))]
        (let [calls (atom 0)
              receiver (fake-transport limit)
              error
              (with-redefs
                [client/receive-at-most!
                 (fn [_ _]
                   (swap! calls inc)
                   (byte-array-of octets))]
                (try
                  (transport/recv receiver)
                  nil
                  (catch Throwable error
                    error)))
              valid?
              (and
               (= :frame-limit-exceeded
                  (:nrepl.transport/error (ex-data error)))
               (= {:limit limit :unread 0 :chunk (inc limit)}
                  (select-keys
                   (ex-data error)
                   [:limit :unread :chunk]))
               (= 1 @calls)
               (zero? (bytes/remaining @(:buf receiver))))]
          (when-not valid?
            (fail! "nrepl.transport/limit:pre-allocation"
                   "oversized socket chunk crossed the frame limit"
                   {:limit limit
                    :calls @calls
                    :remaining (bytes/remaining @(:buf receiver))
                    :error (some-> error ex-data)}))))))))

(defn- frame-limit-shrink-control! []
  (let [result
        (h/run-test!
         {:test-cases 100
          :seed 20260802
          :name "nrepl.transport/frame-limit-off-by-one-control/v1"
          :database ""
          :verbosity :quiet}
         (fn [_]
           (g/let [limit (g/integer 0 256)]
             (let [chunk-count (inc limit)
                   case {:limit limit :chunk chunk-count}
                   buggy-accepted?
                   ;; Deliberate one-byte-over-limit acceptance.
                   (<= chunk-count (inc limit))]
               (when buggy-accepted?
                 (fail! "nrepl.transport/control:frame-limit-off-by-one"
                        "deliberately buggy limit admitted one extra byte"
                        {:case case}))))))]
    (when (:passed? result)
      (throw
       (ex-info "frame-limit buggy control unexpectedly passed" {})))
    (when (:flaky? result)
      (throw
       (ex-info "frame-limit buggy control was flaky"
                {:result result})))
    (let [failure (first (:failures result))
          minimized-case (-> failure :exception ex-data :case)]
      (when-not
       (and (:reproduced? failure)
            (= {:limit 0 :chunk 1} minimized-case))
        (throw
         (ex-info "frame-limit control found a different minimum"
                  {:actual minimized-case :failure failure}))))
    result))

(defn -main [& _]
  (try
    (let [chunks (chunk-regrouping-property!)
          truncated (truncated-eof-property!)
          limit (frame-limit-property!)
          limit-control (frame-limit-shrink-control!)]
      (println
       (pr-str
        {:status :verified
         :runtime :jolt-hegel
         :chunk-regrouping-cases (:valid-test-cases chunks)
         :truncated-eof-cases (:valid-test-cases truncated)
         :frame-limit-cases (:valid-test-cases limit)
         :frame-limit-shrink-minimum
         (-> limit-control :failures first :exception ex-data :case)
         :frame-limit-control-flaky? (:flaky? limit-control)}))
      (flush)
      (System/exit 0))
    (catch Throwable error
      (println
       (pr-str
        {:status :failed
         :message (ex-message error)
         :data (ex-data error)}))
      (flush)
      (System/exit 1))))
