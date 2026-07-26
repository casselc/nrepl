(ns nrepl.transport
  "Client-side byte-native bencode transport over jolt-tcp.

  `connect` opens a connection to a running nREPL server; `send`/`recv` move
  complete messages. The server side lives in jolt core (`jolt.nrepl`)."
  (:require [jolt.bytes :as bytes]
            [nrepl.bencode :as bencode]
            [teensyp.client :as client]))

(def ^:private bufsize 65536)
(def ^:private connect-timeout-ms 30000)
(def ^:private default-max-frame-bytes 67108864)

(defn- empty-cursor []
  (bytes/cursor (bytes/window (byte-array 0))))

(defn- append-chunk
  "Copy only the unread suffix and the new socket chunk into a fresh Cursor."
  [cursor chunk max-frame-bytes]
  (let [view (bytes/cursor-window cursor)
        position (bytes/cursor-position cursor)
        unread (bytes/slice view position)
        unread-count (count unread)
        chunk-count (alength chunk)]
    (when-not
     (and (<= unread-count max-frame-bytes)
          (<= chunk-count (- max-frame-bytes unread-count)))
      (throw
       (ex-info
        "nREPL frame exceeds the transport buffer limit"
        {:nrepl.transport/error :frame-limit-exceeded
         :limit max-frame-bytes
         :unread unread-count
         :chunk chunk-count})))
    (let [result (byte-array (+ unread-count chunk-count))]
      (bytes/copy-into! unread result)
      (System/arraycopy chunk 0 result unread-count chunk-count)
      (bytes/cursor (bytes/window result)))))

(defn connect
  "Open a connection to an nREPL server.

  The public arity remains `(connect host port)`. Connection establishment uses
  one 30-second monotonic deadline across resolution and all address candidates.
  Returns an opaque transport."
  [host port]
  {:connection
   (client/connect
    host port {:connect-timeout-ms connect-timeout-ms})
   :buf (atom (empty-cursor))
   :max-frame-bytes default-max-frame-bytes})

(defn send
  "Encode and send one complete message map over `transport`.

  `teensyp.client/send-all!` handles partial native writes and FIFO-serializes
  this whole-message call with concurrent sends on the same connection."
  [{:keys [connection]} message]
  (client/send-all! connection (bencode/encode-bytes message))
  nil)

(defn- invalid-frame! [result]
  (throw
   (ex-info
    "invalid bencode received from nREPL peer"
    {:nrepl.transport/error :invalid-frame
     :reason (:reason result)
     :offset (:offset result)})))

(defn recv
  "Receive the next message from `transport`, blocking until one is available
  or the connection closes (then nil).

  Complete messages commit the returned immutable Cursor. Incomplete frames
  retain only their unread suffix when another socket chunk arrives. Malformed
  frames fail instead of being confused with incomplete input."
  [{:keys [connection buf max-frame-bytes]}]
  (loop []
    (let [result (bencode/decode-cursor @buf)]
      (case (:status result)
        :ok
        (do
          (reset! buf (:cursor result))
          (:value result))

        :invalid
        (invalid-frame! result)

        :need-more
        (when-let [chunk
                   (client/receive-at-most! connection bufsize)]
          (reset!
           buf
           (append-chunk @buf chunk max-frame-bytes))
          (recur))))))

(defn close [{:keys [connection]}]
  (client/close! connection)
  nil)
