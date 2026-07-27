# Platform coverage

This document records evidence for the byte-native nREPL integration. It is not
a claim that every transitive implementation or operating-system behavior has
been proved.

## Evidence anchor

The first complete hosted matrix passed on 2026-07-27:

- nREPL revision
  [`3f48bda95387a77e86025edb84ce4ece69b3bad2`](https://github.com/casselc/nrepl/commit/3f48bda95387a77e86025edb84ce4ece69b3bad2);
- GitHub Actions
  [run 30279195003](https://github.com/casselc/nrepl/actions/runs/30279195003),
  with all seven jobs successful;
- proposal Jolt core
  [`89fe46e8a826b60b69d264fab76c864881055830`](https://github.com/casselc/jolt/commit/89fe46e8a826b60b69d264fab76c864881055830);
- official Chez Scheme 10.4.1 built from source on every Jolt target; and
- `JOLT_AOT_CACHE=0`, so these are source-runtime claims rather than packaged
  `joltc` or AOT-cache claims.

The public application dependency graph was:

- `casselc/jolt-bencode` at
  [`17858cdbdbe1287cd9be10437549a8d3d72bb554`](https://github.com/casselc/jolt-bencode/commit/17858cdbdbe1287cd9be10437549a8d3d72bb554);
- its transitive `casselc/jolt-bytes` at
  [`b66826131324df1693c17c5aba01d69e0b2186a5`](https://github.com/casselc/jolt-bytes/commit/b66826131324df1693c17c5aba01d69e0b2186a5);
- `casselc/jolt-tcp` at
  [`f0e73381e4e715e10a0e07cc1e93227026d7bb3b`](https://github.com/casselc/jolt-tcp/commit/f0e73381e4e715e10a0e07cc1e93227026d7bb3b);
- its transitive `casselc/jolt-net` at
  [`bd9865c3e6c73f8ec3dcfad8c00f718bd1973c46`](https://github.com/casselc/jolt-net/commit/bd9865c3e6c73f8ec3dcfad8c00f718bd1973c46);
  and
- `chucklehead-dev/jolt-hegel` at
  [`e03127174bcaea4ffa1c0cef11bde0efa009e9dc`](https://github.com/chucklehead-dev/jolt-hegel/commit/e03127174bcaea4ffa1c0cef11bde0efa009e9dc),
  using libhegel 0.30.1.

## Hosted results

| Target | Runtime evidence | Generated evidence |
| --- | --- | --- |
| JVM Clojure 1.12.2, Linux x86-64 | Byte codec compatibility: 9 tests, 35 assertions, no failures or errors | Not run |
| Jolt, Linux x86-64 | Complete client/server and middleware suite over real loopback: 35 tests, 99 assertions, no failures or errors | Hegel: 400 chunk-regrouping, 300 truncated-EOF, and 300 frame-limit cases |
| Jolt, Linux ARM64 | Complete client/server and middleware suite over real loopback: 35 tests, 99 assertions, no failures or errors | Hegel: 400/300/300 cases |
| Jolt, macOS ARM64 | Complete client/server and middleware suite over real loopback: 35 tests, 99 assertions, no failures or errors | Hegel: 400/300/300 cases |
| Jolt, macOS x86-64 | Complete client/server and middleware suite over real loopback: 35 tests, 99 assertions, no failures or errors | Hegel: 400/300/300 cases |
| Jolt, Windows x86-64 | Native PowerShell/source-mode complete client/server and middleware suite over real loopback: 35 tests, 99 assertions, no failures or errors | Hegel: 400/300/300 cases |
| Jolt, Windows ARM64 | Native `tarm64nt` source-mode byte codec compatibility: 9 tests, 35 assertions, no failures or errors | Not run |

Every Hegel lane also reproduced the deliberate frame-limit control's minimal
counterexample as `{:limit 0 :chunk 1}` and reported it non-flaky. The full
suite starts Jolt's in-process nREPL server, connects through the public
`jolt-tcp` client stack, and exercises the middleware. The Windows x86-64
runner invokes Chez directly from PowerShell and observes the real child exit
code; MSYS2 is used only to build Chez.

## What the matrix establishes

The full-runtime lanes establish, for the checked revisions:

- the public Git dependency graph resolves without sibling worktrees;
- byte-native bencode framing composes with the immutable Window/Cursor API;
- the nREPL client composes with `jolt-tcp` and the target's `jolt-net`
  readiness implementation;
- a real loopback client/server exchange reaches Jolt's core server and this
  repository's middleware;
- arbitrary regrouping supplied at the TCP client's receive boundary preserves
  concatenated messages;
- clean EOF and truncated-frame EOF remain distinct; and
- the 64 MiB frame limit rejects before allocating the next combined buffer.

The JVM lane is intentionally a codec compatibility gate. It does not claim a
JVM implementation of the Jolt TCP client or server middleware.

## Proof and oracle custody

The proof artifacts stay with the layer whose semantics they describe:

- the pinned `jolt-bytes` revision contains the Window/Cursor Ansatz sources,
  immutable generated oracles, provenance and digest checks, bounded copy
  models, and runtime conformance fixtures in
  [`docs/PROOFS-AND-ORACLES.md`](https://github.com/casselc/jolt-bytes/blob/b66826131324df1693c17c5aba01d69e0b2186a5/docs/PROOFS-AND-ORACLES.md);
- the pinned `jolt-bencode` revision contains the Ansatz framing oracle,
  exhaustive codec oracle, and paired corrected/buggy/non-vacuity SMT models in
  [`docs/PROOFS-AND-ORACLES.md`](https://github.com/casselc/jolt-bencode/blob/17858cdbdbe1287cd9be10437549a8d3d72bb554/docs/PROOFS-AND-ORACLES.md);
- the pinned `jolt-tcp` revision contains bounded reactor, client deadline,
  ownership, EOF, admission, shutdown, and monotonic wake-cursor models under
  [`docs/proofs/`](https://github.com/casselc/jolt-tcp/tree/f0e73381e4e715e10a0e07cc1e93227026d7bb3b/docs/proofs);
  and
- the pinned `jolt-net` revision contains its socket lease, descriptor,
  readiness, nonblocking, Winsock initialization, wake transport, close, and
  wake-cursor models under
  [`docs/proofs/`](https://github.com/casselc/jolt-net/tree/bd9865c3e6c73f8ec3dcfad8c00f718bd1973c46/docs/proofs).

This repository does not copy those artifacts and does not rerun Ansatz or the
SMT models. It consumes their pinned implementations and adds integration-level
examples and Hegel properties. Passing this matrix is conformance evidence for
that composition, not a re-proof of the dependencies or a proof of native ABI
behavior.

## Deliberate boundaries

- Windows ARM64 is a gating native source-runtime claim for bencode and bytes
  only. The transitive `jolt-net` socket descriptor ABI has not yet been
  reviewed on that target, so this lane does not load `jolt-tcp` namespaces,
  open sockets, run an nREPL server, or install Hegel.
- No lane claims packaged `joltc`, AOT-cache portability, or installation of a
  binary Jolt distribution. AOT is disabled throughout.
- Jolt core's built-in server still presents its historical Latin-1 string
  boundary to middleware. The outbound client and incremental parser are
  byte-native; the integration suite pins the core boundary rather than
  silently redefining it.
- The Ansatz evidence proves and exhaustively checks bounded pure semantics
  under its recorded assumptions. It does not prove Ansatz extraction,
  mutable aliasing, machine-integer behavior outside the checked domains,
  native FFI layouts, scheduler fairness, or operating-system delivery.
- Native socket and wake behavior is established by platform runtime gates and
  bounded lifecycle models together; neither form of evidence substitutes for
  the other.

## Reproduction

With the pinned proposal core available as `jolt`:

```sh
clojure -Srepro -M:jvm-bencode-test
jolt -M:portable-test
jolt -M:test
jolt -A:hegel -m hegel.install
jolt -M:hegel
```

On native Windows, use `tools/test-windows-source.ps1` with explicit project,
runtime, and Chez 10.4.1 paths. The hosted workflow is the canonical executable
record for the exact target matrix and environment boundaries above.
