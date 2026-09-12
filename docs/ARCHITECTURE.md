# Architecture

The decisions, and what they cost.

---

## 1. Interpret, don't execute

**The requirement:** variable and array state at every executed line, for arbitrary user code.

**The options:**

| Approach | How you get the trace | Why it was rejected |
|---|---|---|
| `Runtime.exec` a compiled class | You don't — you get stdout | No state, and it runs arbitrary code in-process |
| JDI debugger on a sandboxed JVM | Step events from a real JVM | Runs arbitrary code. Needs container isolation, CPU/memory/PID limits, a network namespace, a read-only rootfs — and a JVM per run |
| Bytecode instrumentation | Injected callbacks | Same: arbitrary code still runs. Plus an agent to maintain |
| **Interpret a Java subset** | Falls out of evaluation | **Chosen** |

**What interpreting buys:**

1. **There is nothing to escape from.** No child process, no classloading, no reflection. The
   interpreter has no syntax for files, sockets or threads — those are not blocked by policy,
   they are absent from the grammar. `Builtins` is an allowlist of pure functions;
   `Math.random()` is refused because a trace must replay identically when reopened from history
   days later.
2. **Limits that hold.** Every statement and loop iteration passes through `ExecutionBudget`.
   An infinite loop stops after 200,000 steps regardless of what it contains. An oversized
   allocation is refused before memory is touched.
3. **The trace is a by-product.** Line numbers are exact because every AST node carries one.
   Messages are accurate because they are written at the moment the statement runs, by the code
   that ran it — not reconstructed afterwards from two state snapshots.

**What it costs, plainly:** only the subset runs. `docs/SUPPORTED_JAVA_SUBSET.md` is the
contract, `Parser` and `Builtins` enforce it, and both reject what they do not support with a
message naming the missing feature rather than a generic error.

**Where this stops being enough:** the moment any executor runs real code — a JVM-backed Java
executor, or the Python and C++ executors on the roadmap. At that point `ExecutionSandbox` must
be replaced with container-per-run isolation. Its javadoc says so, and the `CodeExecutor`
interface is the seam that makes it a contained change.

---

## 2. The trace contract is the product boundary

```
CodeExecutor  ──►  ExecutionTrace  ──►  every frontend visualizer
```

`ExecutionTrace` contains no Java-specific field. A step is a line number, an action, a message,
scalar variables, drawable structures, and the cells it touched.

**Consequences, all of them intentional:**

- Adding Python is one `@Component` implementing `CodeExecutor`. `ExecutorRegistry` picks it up
  from the Spring context; no switch statement anywhere learns about it. The visualizers,
  playback engine, timeline and history pages are untouched.
- A stored trace replays without re-executing anything. History is cheap, deterministic, and
  costs no credits.
- The frontend cannot drift from the backend's idea of what happened, because it is not
  computing anything — including the cell colours, which come from
  `Highlighter.apply(visualizations, touches)` on the server.

**Where the layering is loose:** `trace.ExecutionTrace` references `entity.ExecutionStatus`.
That enum is a shared vocabulary used by the database, the API and the frontend; duplicating it
to keep packages pure would create two enums to keep in sync. Noted rather than hidden.

---

## 3. Tracing granularity: one step per statement

`TraceBuilder` uses an open/annotate/commit cycle per statement. Annotations (`note`, `touch`,
`print`) land on the innermost open statement; `commit` emits the event with the state the
statement produced.

**Two problems this had to solve:**

**Nesting.** A statement can contain a call whose body is more statements. So staging is a
*stack*, and an outer statement's event is emitted after the inner ones it caused. Combined with
the synthetic `CALL`/`RETURN` markers the interpreter emits around a call, the order reads the
way a debugger does: call site, callee body, return, then the call site's result.

**Which label wins.** A statement can compare, read and write all at once. `note` keeps the most
specific report — `SWAP` > `ARRAY_WRITE` > `COMPARE` > `ARRAY_READ` > `ASSIGN` > `DECLARE` >
`CONDITION` > `STATEMENT` — so the label describes the interesting thing rather than whichever
thing happened last.

**Element comparisons vs conditions.** A relational operator is counted as a comparison only
when at least one operand is an array cell. `arr[j] > arr[j+1]` counts; `i < arr.length` does
not. That makes `metrics.comparisons` directly comparable to a textbook analysis — bubble sort on
6 items reports exactly 15 — which is what lets the UI turn "O(n²)" from a label into something a
learner verifies by re-running with a longer array.

---

## 4. Swap detection: the one inference, kept narrow

Students think in swaps. Java has no swap, only three statements. `SwapDetector` recognises
exactly that shape and relabels the third statement as `SWAP`:

```java
int temp = arr[j];      // reads  arr[j]   -> v1
arr[j] = arr[j + 1];    // reads  arr[j+1] -> v2, writes arr[j] = v2
arr[j + 1] = temp;      // writes arr[j+1] = v1
```

Three properties make this safe to ship:

1. **It verifies values, not just shape.** Each `Touch` carries the value read or written, and
   the match requires `writeA.value == readB.value && writeB.value == readA.value`. A lookalike
   sequence that writes an unrelated value is not relabelled — there is a test for exactly that.
2. **It cannot corrupt anything.** It only rewrites a label and a highlight. All three statements
   remain separate steps, so stepping still matches the source line for line.
3. **It fails safe.** A swap written any other way does not match and renders as plain reads and
   writes: still correct, just less editorialised.

This is deliberately the *only* place in the pipeline that infers intent from a pattern. Every
other message is a literal description of what one statement did.

---

## 5. Frames, scopes, and a bug worth remembering

Each `Frame` owns a chain of `Environment` scopes. Blocks open and close scopes within a frame;
methods push and pop frames. Method frames do not chain to their caller, so a method cannot see
its caller's locals — only static fields, which live in a separate `globals` environment.

The first implementation had `popScope()` resolve the frame with `frames.peek()`. That is correct
on the happy path and wrong the moment an exception unwinds out of a nested call: `peek()` is then
the *callee's* frame, so every enclosing block's `finally` popped scopes off the wrong frame and
walked its chain past the root, producing a `NullPointerException` that surfaced as
`INTERNAL_ERROR`. Runaway recursion — which should report `LIMIT_EXCEEDED` — hit it.

`openScope()` now returns the frame and `closeScope(frame)` takes it back, so open and close are
pinned to the same frame however execution leaves the block. `InterpreterFailureTest`
covers it. The general lesson: with an explicit stack, *"which frame"* must be captured at
open time, never re-derived at close time.

Relatedly, `invoke` pops its frame only on normal completion. If the callee threw, leaving the
frame on the stack is what lets the `ERROR` step report the line and locals where the failure
actually happened.

---

## 6. Transactions and the request path

`ExecutionService` is deliberately **not** `@Transactional`. Interpreting can take seconds, and
holding a pooled connection plus an open transaction for that long is how a connection pool dies
under load. Each database interaction is its own short transaction, with execution in between.

Two consequences:

- **Recording is a separate bean.** `ExecutionRecorder` exists because `@Transactional` is applied
  by a proxy: a self-invoked method inside `ExecutionService` would have silently run with no
  transaction at all. Crossing a bean boundary is what makes the annotation take effect.
- **Recording is best effort.** A run that succeeded and is on its way to the screen must not turn
  into an error because the history write failed. It is logged and the response still carries the
  trace.

Credit mutations use `findByIdForUpdate` (`PESSIMISTIC_WRITE`). Without the row lock, two
concurrent requests can both read the same balance and both succeed, letting a user spend credits
they do not have and leaving the ledger disagreeing with `users.credit_balance`.

---

## 7. Security

| Concern | Decision |
|---|---|
| Arbitrary code execution | Removed by construction — see §1 |
| Auth | Stateless JWT (HS256). Secret from the environment; startup fails if under 32 bytes |
| CSRF | Off, correctly: there is no cookie session to forge against. The token travels in an `Authorization` header the browser does not attach automatically |
| Account enumeration | `hideUserNotFoundExceptions` plus one message for both wrong-password and no-such-account, so neither the body nor the timing distinguishes them |
| IDOR | Every user-owned lookup is scoped in the query (`findByIdAndUserId`). Loading by id and then comparing owners is how these bugs happen; making ownership part of the query removes the code path where the check can be forgotten. A guessed id returns 404 |
| Password storage | BCrypt, cost 10 |
| Secrets in the browser | The Anthropic key is only read server-side. The frontend calls `/api/ai/explain` |
| Error leakage | 4xx messages are written for users; the 5xx handler logs the exception and returns a generic message |
| Unbounded responses | Page size capped at 100. `?size=100000` is otherwise a free way to make the server serialise an entire history |
| Untrusted markdown | AI output renders through `MiniMarkdown`, which builds React elements only. Nothing is ever inserted as HTML, so model-authored text cannot become markup |

**Known gaps, named rather than glossed:** there is no rate limiting on anonymous execution (a
per-IP bucket is the fix, and the reason `ALGOLENS_ALLOW_ANONYMOUS_EXECUTION` exists), no refresh
tokens (a revoked role takes effect at token expiry, which is why the TTL is configurable), and no
email verification.

---

## 8. Objects

### The object model, and the two hazards it introduced

Objects arrived in phase 2 (see `docs/ROADMAP.md`). Two things about them shape the code:

**Everything entering a trace event is flattened first.** `ObjectValue` holds field slots that
hold other `ObjectValue`s, so handing one to Jackson serialises the whole object graph — and a
cyclic linked list, the exact structure people build to test cycle detection, recurses until the
request dies. One `jsonSafe` funnel in `Interpreter` converts every value crossing that boundary
into a primitive or a string. It is a single small method, and it is the reason a cycle renders
as an edge back to an existing node id rather than an infinite chain.

**`toString()` is never called implicitly.** Building a trace message must not execute
interpreted code: that would run the user's side effects and charge the step budget merely to
label a step. Objects render structurally and one level deep. The cost is a documented deviation
from Java; the alternative was messages that can loop forever or mutate state.

**Layout is chosen by field name, not runtime shape.** `next` means a list, `left`/`right` means
a tree. Inferring from shape looks smarter and is worse: a tree whose right subtree is
momentarily empty is structurally a list, so the picture would reclassify itself and jump around
as nodes are inserted.

## 9. Known limitations

| Limitation | Why it is acceptable now | The fix |
|---|---|---|
| Every step carries a full state snapshot | Fine for the MVP's sizes; the 20,000-event cap bounds the worst case | Delta-encode steps; the frontend already replays sequentially |
| Object graphs are capped at 300 nodes per step | Well past what is readable on screen anyway | Delta encoding makes a higher cap affordable |
| Overloads resolve on argument count only | Covers essentially all DSA code, and same-arity overloads are rejected up front rather than mis-dispatched | Resolve on argument types too |
| No collections | The largest remaining gap — BFS needs a queue | Phase 3 |
| 2D array cells are not highlighted | A `Touch` identifies a cell by one index — unambiguous for 1D, not for row/column. The grid renders uncoloured: plain and correct beats colourful and wrong | Add an optional row to `Touch` |
| Pointer arrows use a name allowlist | Drawing an arrow for every in-range int would bury the two that matter | Infer index variables from how they are used |
| No method overloading | Rejected at parse time with a clear message, not silently mis-dispatched | Resolve on arity, then on argument types |
| Cancelled sandbox threads can outlive the request | The interpreter's own budget stops them shortly after | Container-per-run, which is required anyway before real execution |
