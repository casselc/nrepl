# jolt-lang/nrepl

nREPL for [Jolt](https://github.com/jolt-lang/jolt) — the server-side middleware
that grows jolt's built-in nREPL into the full feature set, plus an nREPL client.

Jolt ships a small, extensible nREPL server in core (`joltc nrepl`): bencode over
a socket, with `clone` / `describe` / `eval` / `load-file` / `close`. This library
adds the heavier features as **middleware** — they're optional, so they don't
bloat core:

| Feature | op(s) | ns |
| --- | --- | --- |
| Stateful, isolated sessions | `clone` `close` `ls-sessions` `eval` | `nrepl.middleware.session` |
| Interruptible eval | `interrupt` | `nrepl.middleware.interruptible-eval` |
| Autocomplete | `completions` | `nrepl.middleware.completion` |
| Docs / eldoc | `lookup` | `nrepl.middleware.lookup` |

## Server: use the middleware

Add the dependency and list the middleware in your project's `deps.edn`; jolt
composes them over the built-in handler:

```clojure
{:deps {jolt-lang/nrepl {:git/url "https://github.com/jolt-lang/nrepl"
                         :git/sha "<full-sha>"}}
 :nrepl/middleware [nrepl.middleware/default-middleware]}
```

Then:

```
joltc nrepl          # default port 7888; writes .nrepl-port
joltc nrepl 12345    # explicit port
```

Connect your editor (CIDER / Calva / Cursive) to the port in `.nrepl-port`. Your
project's deps are on the source roots and its native libs are loaded, so
`(require '[some.lib])` works in the session.

Each session keeps its own current namespace and runs evals on a dedicated
serialized worker thread, so sessions are isolated and a long eval doesn't block
other ops. `interrupt` aborts the running eval and the session keeps serving.

## Client

The client transport is implemented over jolt-tcp's portable outbound byte
stream and the incubating `jolt-bencode`/`jolt-bytes` libraries. It keeps an
immutable Cursor over unread bytes, reuses the same backing Window for
concatenated messages, and copies only a partial suffix when another socket
chunk arrives. nREPL framing therefore contains no runtime-specific socket FFI
or descriptor handling of its own.

```clojure
(require '[nrepl.core :as nrepl])

(let [t (nrepl/connect "127.0.0.1" 7888)
      s (nrepl/new-session t)]
  (nrepl/response-values (nrepl/message t {:op "eval" :code "(+ 1 2)" :session s}))  ;=> [3]
  (nrepl/close t))
```

`message` sends a message (an `:id` is added) and returns the responses for it up
to `"done"`. `combine-responses`, `response-values`, `new-session`, and the
`code` macro mirror the official `nrepl.core`.

`nrepl.bencode/encode-bytes`, `decode-bytes`, and `decode-cursor` expose the
byte-native codec. Incremental decoding distinguishes `:need-more` from
`:invalid`; both preserve the exact original Cursor. The historical Latin-1
`encode`/`decode` facade remains for source compatibility, but malformed input
now throws instead of being mistaken for an incomplete frame. The transport
also bounds an accumulated frame at 64 MiB before allocating the next buffer.

This incubation branch consumes `../jolt-bencode` through a local dependency.
That must become a published coordinate or an upstream stdlib placement before
the branch is independently consumable.

## Notes for jolt

`interrupt` aborts a compute-bound eval at the next safe point, but it can't
preempt one blocked in a foreign call (socket recv, sleep) — that aborts only once
the call returns to Scheme.

The official nREPL implementation can't run unchanged on jolt — its core is tied
to `java.util.concurrent` executors, compiled Java helper classes, a dynamic
classloader, Compiler internals and a JVMTI agent. This library implements the
same wire protocol and behaviours on jolt-native threads.

## Tests

`joltc -M:test` runs the suite (bencode, client, session, completion, lookup,
interrupt) against an in-process server with the middleware installed.
`clojure -Srepro -M:jvm-bencode-test` checks the compatibility facade and
byte-native codec on JVM Clojure.
