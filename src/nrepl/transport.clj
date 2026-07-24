(ns nrepl.transport
  "Client-side bencode transport over the portable jolt-tcp byte-stream client.
  `connect` opens a connection to a running nREPL server; `send`/`recv` move
  bencode messages. The server side lives in jolt core (jolt.nrepl)."
  (:require [nrepl.bencode :as bencode]
            [teensyp.client :as client]))

(def ^:private bufsize 65536)
(def ^:private connect-timeout-ms 30000)

(defn- wire-bytes [wire]
  (byte-array (map int wire)))

(defn- bytes->wire [bytes]
  (String. bytes "ISO-8859-1"))

(defn connect
  "Open a connection to an nREPL server.

  The public arity remains `(connect host port)`. Connection establishment uses
  one 30-second monotonic deadline across resolution and all address candidates.
  Returns an opaque transport."
  [host port]
  {:connection (client/connect host port
                               {:connect-timeout-ms connect-timeout-ms})
   :buf (atom "")})

(defn send
  "Encode and send one complete message map over `transport`.

  `teensyp.client/send-all!` handles partial native writes and FIFO-serializes
  this whole-message call with concurrent sends on the same connection."
  [{:keys [connection]} msg]
  (client/send-all! connection (wire-bytes (bencode/encode msg)))
  nil)

(defn recv
  "Receive the next message from `transport`, blocking until one is available or
  the connection closes (then nil)."
  [{:keys [connection buf]}]
  (loop []
    (let [r (bencode/decode @buf 0)]
      (if r
        (do (swap! buf subs (second r)) (first r))
        (when-let [chunk (client/receive-at-most! connection bufsize)]
          (swap! buf str (bytes->wire chunk))
          (recur))))))

(defn close [{:keys [connection]}]
  (client/close! connection)
  nil)
