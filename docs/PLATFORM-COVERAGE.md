# Platform coverage

This document records evidence for the byte-native nREPL integration. It is not
a claim that every transitive implementation or operating-system behavior has
been proved.

## Evidence anchor

The current complete hosted matrix passed on 2026-07-27:

- nREPL revision
  [`078769877a1c0ce0aca142bf87b91969758fa111`](https://github.com/casselc/nrepl/commit/078769877a1c0ce0aca142bf87b91969758fa111);
- GitHub Actions
  [run 30333128544](https://github.com/casselc/nrepl/actions/runs/30333128544),
  with all seven jobs successful;
- proposal Jolt core
  [`46e1f74fc14f29283586900ef4b98c45375c0500`](https://github.com/casselc/jolt/commit/46e1f74fc14f29283586900ef4b98c45375c0500);
- official Chez Scheme 10.4.1 built from source on every Jolt target; and
- `JOLT_AOT_CACHE=0`, so these are source-runtime claims rather than packaged
  `joltc` or AOT-cache claims.

The public application dependency graph was:

- `casselc/jolt-bencode` at
  [`7fda49ec750efb80d3c5abb609f807f2248f8cec`](https://github.com/casselc/jolt-bencode/commit/7fda49ec750efb80d3c5abb609f807f2248f8cec);
- its transitive `casselc/jolt-bytes` at
  [`c34b4d2275240a1efc7630f94cf97880cb905cc9`](https://github.com/casselc/jolt-bytes/commit/c34b4d2275240a1efc7630f94cf97880cb905cc9);
- `casselc/jolt-tcp` at
  [`911cf783d56e988adb2b8f716b6636fae5454e52`](https://github.com/casselc/jolt-tcp/commit/911cf783d56e988adb2b8f716b6636fae5454e52);
- its transitive `casselc/jolt-net` at
  [`c3747385235df812e0d739a3e9f71c4dfb07b474`](https://github.com/casselc/jolt-net/commit/c3747385235df812e0d739a3e9f71c4dfb07b474);
  and
- `chucklehead-dev/jolt-hegel` at
  [`c406e6a85e9902dd89a42a3abce3d6161e5cd406`](https://github.com/chucklehead-dev/jolt-hegel/commit/c406e6a85e9902dd89a42a3abce3d6161e5cd406),
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
| Jolt, Windows ARM64 | Native `tarm64nt` source-mode complete client/server and middleware suite over real loopback: 35 tests, 99 assertions, no failures or errors | Hegel: 400/300/300 cases |

Every Hegel lane also reproduced the deliberate frame-limit control's minimal
counterexample as `{:limit 0 :chunk 1}` and reported it non-flaky. The full
suite starts Jolt's in-process nREPL server, connects through the public
`jolt-tcp` client stack, and exercises the middleware. Both Windows runners
invoke Chez directly from PowerShell and observe the real child exit code;
MSYS2 is used only to build Chez on x86-64. The ARM64 lane additionally fails
closed unless the hosted runner is ARM64, Chez reports `tarm64nt`, and
`jolt.host/target` reports `:aarch64`.

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
  [`docs/PROOFS-AND-ORACLES.md`](https://github.com/casselc/jolt-bytes/blob/c34b4d2275240a1efc7630f94cf97880cb905cc9/docs/PROOFS-AND-ORACLES.md);
- the pinned `jolt-bencode` revision contains the Ansatz framing oracle,
  exhaustive codec oracle, and paired corrected/buggy/non-vacuity SMT models in
  [`docs/PROOFS-AND-ORACLES.md`](https://github.com/casselc/jolt-bencode/blob/7fda49ec750efb80d3c5abb609f807f2248f8cec/docs/PROOFS-AND-ORACLES.md);
- the pinned `jolt-tcp` revision contains bounded reactor, client deadline,
  ownership, EOF, admission, shutdown, and monotonic wake-cursor models under
  [`docs/proofs/`](https://github.com/casselc/jolt-tcp/tree/911cf783d56e988adb2b8f716b6636fae5454e52/docs/proofs);
  and
- the pinned `jolt-net` revision contains its socket lease, descriptor,
  readiness, nonblocking, Winsock initialization, wake transport, close, and
  wake-cursor models under
  [`docs/proofs/`](https://github.com/casselc/jolt-net/tree/c3747385235df812e0d739a3e9f71c4dfb07b474/docs/proofs).

This repository does not copy those artifacts and does not rerun Ansatz or the
SMT models. It consumes their pinned implementations and adds integration-level
examples and Hegel properties. Passing this matrix is conformance evidence for
that composition, not a re-proof of the dependencies or a proof of native ABI
behavior.

## Deliberate boundaries

- Windows ARM64 is a gating native source-runtime claim for the complete
  dependency graph, real loopback sockets, nREPL middleware, and Hegel
  properties. It is not a packaged-Jolt or AOT-image claim.
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
