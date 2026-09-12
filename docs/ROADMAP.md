# Roadmap

The sequencing principle: **each phase must leave a product someone can use**, and the phase that
unlocks the most later phases comes first.

---

## Shipped — MVP

Java arrays and recursion, visualized end to end.

- Lexer, parser and tree-walking interpreter for the Java subset, with step-level tracing
- Language-independent `ExecutionTrace` contract; `CodeExecutor` seam
- Budget enforcement inside the interpreter (steps, wall clock, output, call depth, allocation)
- Array and matrix visualizers with pointer arrows and per-cell highlighting
- Swap collapsing, value-verified
- Playback: play, pause, step forward and back, scrub, 0.25x–4x, keyboard shortcuts
- Variables, call stack, output and run metrics panels
- Monaco editor with the executing line highlighted
- JWT auth, saved code, replayable run history, dashboard
- Credit ledger, with metering and enforcement separated
- AI explanations in four modes, plus an offline explainer that works with no key
- 12-problem starter library, smoke-tested in CI
- 89 tests: interpreter behaviour, failure paths, seed data, credit invariants, full HTTP journey
- Docker Compose for Postgres + backend + nginx

**Verified working:** bubble sort on `{5,2,8,1}` produces 48 steps, 6 element comparisons and
4 swaps; binary search on 10 elements finds the target in 5 comparisons with `low`/`high`/`mid`
rendered as pointers.

---

## Shipped — Phase 2: the object model, linked lists and trees

The phase that unblocked everything reference-shaped.

- `class` declarations, top level and nested (`static class Node`), flattened to one namespace
- Instance and static fields, field initialisers, constructors
- Instance methods with `this` bound, and implicit `this` on unqualified names
- Overload resolution by argument count, for methods and constructors
- `new Node(...)`, `new Node[n]`, chained field access and assignment
- Reference identity for `==`, and `null` that reports *which* expression was null
- One `ObjectGraphVisualizer` covering linked lists, trees and general object graphs, with the
  layout chosen from reference-field names and cycles drawn as arcs
- `FIELD_READ` / `FIELD_WRITE` / `ALLOCATE` trace actions, and field/allocation counters
- Six new library problems: build/walk a list, find the middle, detect a cycle, reverse a list,
  BST insert and search, in-order traversal

Two bugs this phase surfaced and fixed, both worth remembering:

1. **Serialising an interpreted object would hang the request.** A cyclic list handed to Jackson
   recurses forever. Every value crossing into a trace event now goes through one `jsonSafe`
   funnel that flattens it to a primitive or a string.
2. **The finished picture used to vanish.** `main`'s body ran in a nested block scope, so its
   locals went out of scope the instant the body ended — and the DONE step, the completed
   structure people actually want to look at, showed nothing. The entry method's body now runs
   in the frame's own scope.

## Shipped — Phase 3: collections, generics, switch and exceptions

The phase that made ordinary pasted Java run.

- Generic syntax, parsed and erased, including nested arguments (`Map<String, List<Integer>>`,
  where the closing `>>` is a single token) and the diamond
- `ArrayList`, `LinkedList`, `Stack`, `ArrayDeque`, `PriorityQueue`, the `Map` and `Set`
  families, `StringBuilder`, `Map.Entry`
- `switch` in both forms, with real fall-through; `try`/`catch`/`finally`, `throw`,
  multi-catch; `instanceof`
- `String.format`, `System.out.printf`, `String.join`, `Collections.*`, `List.of`,
  `Arrays.asList`
- New `MAP` and `SET` visualizations; stacks get a `top` marker, queues get `front`/`back`
- Dotted type names, so `java.util.List<Integer>` resolves like `List<Integer>`

Three decisions worth recording:

1. **Collections are interpreter built-ins, not interpreted source.** Shipping them as source
   would let a learner step into `ArrayList.add` — genuinely nice, and still on the table — but
   every list operation would then cost dozens of steps from the budget. Built-ins first,
   because they are what unblock the algorithms.
2. **The declared type picks the drawing.** `Queue<Integer> q = new LinkedList<>()` is the
   standard BFS idiom; drawing it as an indexed list would hide the front and back, which is the
   only thing that makes it behave like a queue.
3. **`Arrays.sort` was un-refused.** An earlier version rejected it so that sorting could not
   happen in one invisible step. That was wrong: plenty of correct code sorts as a
   *precondition*, and refusing it just made working Java fail.

Two hazards this phase introduced and closed:

- **Rendering had to become depth limited.** `list.add(list)` is legal, and so is an object
  field pointing back at its owner. `Values.render` carries a depth budget; past it a structure
  collapses to `[...]`.
- **An uncaught `throw` escaped as an internal signal**, surfacing as "AlgoLens hit an internal
  error" when the truth was that the program threw. It is now converted at the entry point into
  the runtime error it actually is.

## Phase 4 — Lambdas *(next)*

The largest remaining gap, and the one most pasted code now trips on:

```java
list.forEach(x -> System.out.println(x));
map.computeIfAbsent(key, k -> new ArrayList<>()).add(v);
new PriorityQueue<>((a, b) -> b - a);
list.removeIf(x -> x < 0);
```

Needs: a `Lambda` AST node, a closure value capturing its defining scope, and `->` in expression
position (the token already exists, added for arrow switches). Each collection method that
currently refuses by name then becomes a few lines.

Streams are a separate, larger question — `filter`/`map`/`collect` is a pipeline, and how to
visualize one without it becoming an opaque single step is genuinely unresolved.

## Phase 5 — Graphs

BFS, DFS, Dijkstra, topological sort, cycle detection. Adjacency-list and matrix input.

Mostly unblocked by phase 3. The object graph already renders arbitrary reference structures in
breadth-first layers, so the remaining work is highlighting the **frontier and the visited set** —
Dijkstra without a visible frontier is not worth visualizing — and a better layout than layers
for dense graphs.

## Phase 6 — Delta-encoded traces

Today every step carries a full state snapshot. Fine for the MVP's array sizes and bounded by the
20,000-event cap, but it is what limits how large an input can be visualized.

Change: a step carries only what changed, plus a periodic keyframe. The frontend already replays
sequentially, so it can reconstruct state as it steps — and stepping *backwards* is why keyframes
are needed rather than pure deltas.

Expected: an order of magnitude smaller payloads, and much larger inputs becoming practical.

## Phase 7 — Python, then C++

Deliberately after the data structures, not before. One language done thoroughly is worth more
than four done shallowly, and each new structure benefits every language while each new language
benefits only itself.

Both go behind the existing `CodeExecutor` interface and must return the same `ExecutionTrace`.
The frontend does not change.

**Python** is the easier one: `sys.settrace` on a real CPython process gives line events and
frame locals directly. That means real execution, which means **the sandbox story changes** —
container per run, CPU and memory limits, no network namespace, read-only rootfs. See
`ExecutionSandbox`'s javadoc; this phase is where it must be replaced.

**C++** is the hardest: compile with debug info and drive GDB/MI, or instrument the AST with
Clang's tooling. Slow per run, so it needs a warm compiler pool.

## Phase 8 — Product

Only once the core is genuinely good:

- Payments, and **only then** flip `ALGOLENS_CREDITS_ENFORCED` to true
- Rate limiting on anonymous execution (a per-IP bucket) — the reason
  `ALGOLENS_ALLOW_ANONYMOUS_EXECUTION` exists as a switch today
- Shareable read-only trace links, which are the natural growth mechanism for this product: a
  student pastes a link into a study group and the whole run is right there
- Refresh tokens, email verification, password reset
- Side-by-side comparison of two algorithms on the same input, with counters — the single most
  convincing teaching view this product could have
- Progress tracking against the problem library

---

## Explicitly not planned

| Not doing | Why |
|---|---|
| Full Java support | Diminishing returns fast. Lambdas and streams do not visualize usefully, and every addition is grammar to maintain |
| Running untrusted code as-is | The current design's safety comes from *not* doing this. Any real-execution executor gets container isolation, no exceptions |
| A general-purpose debugger | AlgoLens is a teaching visualizer. Conditional breakpoints and watch expressions belong in an IDE |
| Client-side interpretation | Tempting for latency, but it forks the semantics: two interpreters that must agree exactly is a bug factory |

---

## If you want to contribute

The highest-value work in rough order:

1. **Lambdas** (phase 4) — the most common thing people paste that still gets rejected, and it
   unblocks a dozen collection methods at once.
2. **Delta encoding** (phase 6) — it lifts the ceiling on input size for everything.
3. **Better graph layout** — the current breadth-first layers are fine for a tree or a small
   graph and get crowded beyond that. Can be worked on entirely in
   `ObjectGraphVisualizer.tsx` against hand-written fixtures.
4. **More problems** in `ProblemSeeder` — every entry is automatically smoke-tested by
   `ProblemLibraryTest`, so a broken snippet fails the build rather than the demo.
5. **Subset gaps** — `switch` and exceptions are both self-contained; see the "Adding to the
   subset" checklist in `SUPPORTED_JAVA_SUBSET.md`.
