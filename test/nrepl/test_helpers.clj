(ns nrepl.test-helpers
  "Shared harness: one in-process server (built-in handler + this library's
  default middleware), and client helpers, for the ported tests."
  (:require [jolt.nrepl :as server]
            [nrepl.core :as nrepl]
            [nrepl.transport :as transport]))

(def port 7903)

(defonce ^:private server-up
  (delay
    ;; `jolt.nrepl/start` now binds synchronously and returns only after the
    ;; background accept loop has been installed. Wrapping it in another future
    ;; both hides startup exceptions and races the first client unnecessarily.
    (server/start port ['nrepl.middleware/default-middleware])))

(defn conn [] @server-up (nrepl/connect "127.0.0.1" port))

(defn eval-code
  [t code & {:keys [session ns]}]
  (nrepl/message t (cond-> {:op "eval" :code code} session (assoc :session session) ns (assoc :ns ns))))

(defn eval-value
  "Return the single read value of evaluating `code`."
  [t code & opts]
  (first (nrepl/response-values (apply eval-code t code opts))))

(def message nrepl/message)
(def new-session nrepl/new-session)
(def combine nrepl/combine-responses)
(def values nrepl/response-values)
(def send-raw transport/send)
