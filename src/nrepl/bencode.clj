(ns nrepl.bencode
  "nREPL's bencode compatibility facade over the byte-native jolt.bencode
  codec.

  New transport code should use `encode-bytes`, `decode-bytes`, or
  `decode-cursor`. The historical `encode`/`decode` API remains available with
  Latin-1 strings so existing callers do not need an atomic migration."
  (:require [jolt.bencode :as codec]
            [jolt.bytes :as bytes]))

(defn ->wire
  "Convert UTF-8 text to the historical Latin-1 wire-string representation."
  [value]
  (String. (.getBytes (str value) "UTF-8") "ISO-8859-1"))

(defn wire->
  "Decode the historical Latin-1 wire-string representation as UTF-8 text."
  [value]
  (String. (.getBytes value "ISO-8859-1") "UTF-8"))

(defn encode-bytes
  "Encode one nREPL-profile value to a byte array."
  [value]
  (codec/encode value))

(defn decode-cursor
  "Decode one value from a jolt.bytes/Cursor.

  Returns the byte-native codec's explicit `:ok`, `:need-more`, or `:invalid`
  result. Incomplete and invalid results preserve the exact input Cursor."
  ([cursor]
   (codec/decode cursor))
  ([cursor options]
   (codec/decode cursor options)))

(defn decode-bytes
  "Decode one value from the beginning of a byte array."
  ([value]
   (codec/decode-bytes value))
  ([value options]
   (codec/decode-bytes value options)))

(defn encode
  "Compatibility API: encode one value as a Latin-1 wire string."
  [value]
  (String. (encode-bytes value) "ISO-8859-1"))

(defn- invalid-frame! [result]
  (throw
   (ex-info
    "invalid nREPL bencode frame"
    {:nrepl.bencode/error :invalid-frame
     :reason (:reason result)
     :offset (:offset result)})))

(defn decode
  "Compatibility API: decode one value from Latin-1 wire string `value`.

  Returns `[decoded next-index]`, nil for incomplete input, and throws
  `:nrepl.bencode/error :invalid-frame` for malformed input. Use
  `decode-cursor` when the caller needs all three statuses as data."
  ([value]
   (decode value 0))
  ([value index]
   (let [wire (.getBytes value "ISO-8859-1")
         cursor (bytes/cursor (bytes/window wire) index)
         result (decode-cursor cursor)]
     (case (:status result)
       :ok
       [(:value result)
        (bytes/cursor-position (:cursor result))]

       :need-more
       nil

       :invalid
       (invalid-frame! result)))))
