# AlgoLens

**Write DSA code. Press Run. Watch the algorithm execute, one step at a time.**

Instead of `[1, 2, 5, 8]`, you get every step that produced it — the line that ran, the cells it
touched, the variables it changed, and a sentence saying what happened:

```
line 5   COMPARE   arr[0]=5 > arr[1]=2 -> true

  0      1      2      3
┌────┐ ┌────┐ ┌────┐ ┌────┐
│  5 │ │  2 │ │  8 │ │  1 │      i=0  j=0
└────┘ └────┘ └────┘ └────┘
  ▲      ▲
  i,j   compared

line 8   SWAP      Swapped arr[0] and arr[1] -> 2 and 5

  0      1      2      3
┌────┐ ┌────┐ ┌────┐ ┌────┐
│  2 │ │  5 │ │  8 │ │  1 │      i=0  j=0  temp=5
└────┘ └────┘ └────┘ └────┘
  ▲▲▲▲   ▲▲▲▲
  swapped

▶ Play   ⏸ Pause   ◀ Prev   ▶ Next   ⏭ End   ↻ Reset      0.25x 0.5x 1x 2x 4x
step 9 / 48
```

Every number above comes from the real run: 48 steps, 6 element comparisons, 4 swaps.

---

## Table of contents

1. [What you need](#what-you-need)
2. [Run it — the 60 second path](#run-it--the-60-second-path)
3. [Run it — PostgreSQL](#run-it--postgresql)
4. [Run it — Docker](#run-it--docker)
5. [Every command](#every-command)
6. [Configuration](#configuration)
7. [Turning on AI explanations](#turning-on-ai-explanations)
8. [How it works](#how-it-works)
9. [Deploy it for free](#deploy-it-for-free)
10. [Project layout](#project-layout)
10. [API](#api)
11. [What Java is supported](#what-java-is-supported)
12. [Roadmap](#roadmap)
13. [Troubleshooting](#troubleshooting)

---

## What you need

| Tool | Version | Needed for | Notes |
|---|---|---|---|
| **JDK** | 17 or newer | backend | Only hard requirement. `java -version` to check. |
| **Node.js** | 18 or newer | frontend | Ships with npm. |
| Maven | — | — | **Not needed.** `backend/mvnw` downloads it on first use. |
| PostgreSQL | 12+ | optional | The default profile uses embedded H2 instead. |
| Docker | — | optional | Only for the container path. |

No database to install, no Maven to install, no API key needed to start.

---

## Run it — the 60 second path

Two terminals. This uses a file-backed H2 database, so there is nothing to provision.

**Terminal 1 — backend**

```bash
cd backend
./mvnw spring-boot:run          # Windows: .\mvnw.cmd spring-boot:run
```

Wait for `Started AlgoLensApplication`. The first run downloads Maven and the dependencies
(a few minutes); after that it starts in seconds.

**Terminal 2 — frontend**

```bash
cd frontend
npm install
npm run dev
```

**Open <http://localhost:5173>** and press **▶ Run**. The editor starts on bubble sort, so the
first thing you see is the thing the product is for. No sign-up required.

What you get on that first page:

- **Ctrl+Enter** runs. **Space** plays/pauses. **← →** step. **Home/End** jump.
- Click any tick on the timeline to scrub straight to that step.
- The **Problems** tab has 18 ready-to-run algorithms, including linked lists and BSTs.
- Create an account (free) to save code, keep run history, and replay past runs.

---

## Run it — PostgreSQL

Only needed when you want the production-shaped setup. Create the database:

```sql
CREATE DATABASE algolens;
CREATE USER algolens WITH PASSWORD 'algolens';
GRANT ALL PRIVILEGES ON DATABASE algolens TO algolens;
```

Then start with the `postgres` profile:

```bash
cd backend

# macOS / Linux
SPRING_PROFILES_ACTIVE=postgres ./mvnw spring-boot:run

# Windows PowerShell
$env:SPRING_PROFILES_ACTIVE="postgres"; .\mvnw.cmd spring-boot:run
```

Flyway creates the schema on startup. Override `ALGOLENS_DB_URL`, `ALGOLENS_DB_USER` and
`ALGOLENS_DB_PASSWORD` if your setup differs.

---

## Deploy it for free

The whole app ships as **one Docker image** — the React bundle is baked into the Spring Boot jar
and served by it, so there is one service, one URL and no CORS.

| Piece | Where | Free tier |
|---|---|---|
| App (API + UI) | Render | 512 MB, sleeps after 15 min idle |
| Database | Neon | 0.5 GB Postgres, no expiry |

Push to GitHub, then on Render: **New → Blueprint → pick the repo → Apply**.
[`render.yaml`](render.yaml) configures the rest; you only paste the Neon connection details.

Honest caveat: free instances sleep, so the first visit after idle takes 30–60 seconds to wake.

Step-by-step, including alternatives (Koyeb, Cloud Run, Fly.io, split Vercel + Render) and every
environment variable: [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md).

---

## Run it — Docker (local)

The whole stack, production-shaped, in one command:

```bash
cp .env.example .env      # then set ALGOLENS_JWT_SECRET
docker compose up --build
```

Open <http://localhost:3000>. nginx serves the built SPA and proxies `/api` to the backend, so
the browser sees a single origin.

```bash
docker compose logs -f backend    # follow the backend
docker compose down               # stop
docker compose down -v            # stop and wipe the database
```

---

## Every command

### Backend (`cd backend`)

| Command | What it does |
|---|---|
| `./mvnw spring-boot:run` | Start on <http://localhost:8080> (dev profile, H2) |
| `./mvnw test` | Run all 165 tests |
| `./mvnw test -Dtest=InterpreterAlgorithmsTest` | Run one test class |
| `./mvnw package` | Build `target/algolens-backend-0.1.0.jar` |
| `java -jar target/algolens-backend-0.1.0.jar` | Run the built jar |
| `./mvnw clean` | Delete build output |

On Windows use `.\mvnw.cmd` in place of `./mvnw`.

Useful URLs once it is running:

- <http://localhost:8080/swagger-ui.html> — interactive API docs
- <http://localhost:8080/api/meta> — capabilities, limits and prices
- <http://localhost:8080/h2-console> — database browser (dev profile only).
  JDBC URL `jdbc:h2:file:./data/algolens`, user `sa`, no password — replace the prefilled
  `jdbc:h2:~/test`, which is the one field people get wrong. See
  [`docs/DATABASE.md`](docs/DATABASE.md)
- <http://localhost:8080/actuator/health> — health check

### Frontend (`cd frontend`)

| Command | What it does |
|---|---|
| `npm install` | Install dependencies |
| `npm run dev` | Dev server on <http://localhost:5173> with hot reload |
| `npm run build` | Type-check, then build to `dist/` |
| `npm run preview` | Serve the production build locally |
| `npm run typecheck` | Type-check only |

The dev server proxies `/api` to `localhost:8080`. Point it elsewhere with
`VITE_API_TARGET=http://host:port npm run dev`.

### Verify it end to end without a browser

```bash
curl -s -X POST http://localhost:8080/api/executions \
  -H 'Content-Type: application/json' \
  -d '{"language":"JAVA","code":"int[] a = {2,1};\nint t = a[0];\na[0] = a[1];\na[1] = t;\n"}'
```

You should get back a trace whose final step shows `[1, 2]` and one step labelled `SWAP`.

---

## Configuration

Everything is an environment variable; nothing secret lives in the repo. Defaults are in
[`backend/src/main/resources/application.yml`](backend/src/main/resources/application.yml).

| Variable | Default | Purpose |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` | `dev` (H2) or `postgres` |
| `SERVER_PORT` | `8080` | Backend port |
| `ALGOLENS_JWT_SECRET` | dev-only value | **Set this in any real deployment.** HS256 key, minimum 32 bytes — the app refuses to start with less |
| `ALGOLENS_JWT_EXPIRY_MINUTES` | `1440` | Token lifetime |
| `ALGOLENS_DB_URL` / `_USER` / `_PASSWORD` | local Postgres | Used by the `postgres` profile |
| `ALGOLENS_CORS_ORIGINS` | localhost dev ports | Comma-separated allowed origins |
| `ANTHROPIC_API_KEY` | *(empty)* | Enables real AI explanations |
| `ALGOLENS_AI_MODEL` | `claude-opus-5` | Model to use |
| `ALGOLENS_CREDITS_ENFORCED` | `false` | When `true`, credits actually refuse requests |
| `ALGOLENS_ALLOW_ANONYMOUS_EXECUTION` | `true` | When `false`, running code requires an account |

### Execution limits

Enforced inside the interpreter, so they cannot be talked around:

| Limit | Default |
|---|---|
| Code length | 20,000 chars |
| Interpreter steps | 200,000 |
| Trace events kept | 20,000 |
| Wall clock | 5 s |
| Program output | 20,000 chars |
| Call depth | 200 frames |
| Array length | 10,000 |

Exceeding one is a normal outcome, not an error page: you get a partial trace, a
`LIMIT_EXCEEDED` status, and a message saying which limit it was.

---

## Turning on AI explanations

The AI panel works out of the box using a built-in **local explainer** — no key, no network, no
charge. It reads the trace and the shape of your code and says only what it can establish. Every
answer it gives is labelled as a local analysis.

For real explanations, set a key on the **backend**:

```bash
# macOS / Linux
export ANTHROPIC_API_KEY=sk-ant-...

# Windows PowerShell
$env:ANTHROPIC_API_KEY="sk-ant-..."
```

Restart the backend. `/api/meta` will report `"aiProvider": "claude"` and the panel stops showing
the local-analysis notice.

The key is only ever read server-side and is never sent to the browser — the frontend calls
`POST /api/ai/explain` and the backend calls Anthropic. Requests carry your code plus a rendered
window of your own trace; no account details are included.

Four modes: **explain this step**, **explain the algorithm**, **complexity**, **find my bug**. Each
is grounded in the trace, because the biggest risk with an AI explainer over a visualizer is a
fluent description of how bubble sort *generally* works that contradicts the run on screen. The
system prompt makes the trace the ground truth.

---

## How it works

```
  Browser                        Spring Boot                      Interpreter
┌──────────────┐   POST        ┌──────────────────┐            ┌─────────────────┐
│ Monaco       │ /api/         │ ExecutionService │            │ Lexer           │
│ editor       │ executions    │  authorize       │            │   ↓             │
│              │ ────────────► │  meter (credits) │ ─────────► │ Parser → AST    │
│              │               │  execute         │            │   ↓             │
│ visualizers  │ ◄──────────── │  record          │ ◄───────── │ Interpreter     │
└──────────────┘ ExecutionTrace└──────────────────┘  trace     │  + budget       │
                                        │                      └─────────────────┘
                                        ▼
                                  PostgreSQL / H2
                            (users, history, ledger, problems)
```

### The one design decision everything else follows from

**User code is interpreted, never executed.**

The product needs variable and array state at every line. Getting that from a real JVM means
either attaching a debugger to a sandboxed process or instrumenting bytecode — both of which run
arbitrary user code and inherit every sandbox-escape, resource-exhaustion and side-effect problem
that comes with it.

So AlgoLens ships a lexer, parser and tree-walking interpreter for a deliberately small Java
subset. That buys three things at once:

- **Nothing to escape from.** No child process, no classloading, no reachable syntax for files,
  network or threads. Not blocked by policy — absent from the grammar.
- **Limits that actually hold.** Every loop iteration passes through `ExecutionBudget`. An
  infinite loop stops after 200,000 steps whatever it does.
- **The trace is free.** It falls out of evaluation rather than being reconstructed afterwards,
  which is why every step has an exact line number and an accurate message.

The honest cost: only the subset runs. `Arrays.sort` is refused *on purpose* — it would collapse
the algorithm into one invisible step, which is the opposite of the point. See
[`docs/SUPPORTED_JAVA_SUBSET.md`](docs/SUPPORTED_JAVA_SUBSET.md).

One consequence worth knowing about: because objects are interpreted values rather than real
ones, every value that crosses into a trace event is flattened to a primitive or a string first.
A cyclic linked list — exactly what people build to test cycle detection — would otherwise send
a JSON serialiser into an infinite loop.

`ExecutionSandbox` is still a real boundary (dedicated thread, 16 MB stack, hard wall-clock
backstop), and its javadoc states plainly what must replace it before any executor runs real
code. See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

### The trace contract

One JSON shape, with nothing Java-specific in it:

```json
{
  "language": "JAVA",
  "status": "SUCCESS",
  "totalSteps": 48,
  "stdout": "Sorted: [1, 2, 5, 8]\n",
  "metrics": { "comparisons": 6, "swaps": 4, "arrayReads": 20, "arrayWrites": 8 },
  "steps": [
    {
      "step": 9,
      "line": 8,
      "action": "SWAP",
      "message": "Swapped arr[0] and arr[1] -> 2 and 5",
      "variables": [{ "name": "j", "type": "int", "value": 0, "changed": false }],
      "visualizations": [{
        "type": "ARRAY",
        "name": "arr",
        "elements": [
          { "index": 0, "value": 2, "state": "SWAPPING" },
          { "index": 1, "value": 5, "state": "SWAPPING" }
        ],
        "pointers": [{ "name": "j", "index": 0 }]
      }],
      "touches": [{ "target": "arr", "index": 0, "kind": "SWAP", "value": 2 }]
    }
  ]
}
```

The frontend never asks which language produced this. Adding Python means adding one
`CodeExecutor` implementation; the visualizers, playback and history pages do not change.

Two details worth knowing:

- **`comparisons` counts element comparisons only.** `arr[j] > arr[j+1]` counts; `i < arr.length`
  does not. That matches what a complexity analysis means, so the counter is directly comparable
  to the textbook number — bubble sort on 6 items reports exactly 15.
- **The three-statement temp swap is collapsed into one `SWAP` step.** This is the single place
  anything infers intent, and it verifies that the values genuinely exchanged rather than matching
  on index shape alone. A swap written some other way shows up as plain reads and writes: still
  correct, just less editorialised.

---

## Project layout

```
AlgoLens/
├── backend/
│   ├── mvnw, mvnw.cmd, .mvn/          Maven wrapper — no Maven install needed
│   └── src/main/java/com/algolens/
│       ├── execution/
│       │   ├── CodeExecutor.java      the seam every language plugs into
│       │   ├── JavaSubsetExecutor.java
│       │   ├── ExecutionSandbox.java  isolation boundary
│       │   └── interpreter/           Lexer, Parser, Ast, Interpreter, Values, Builtins
│       ├── trace/                     the language-independent contract
│       │   ├── ExecutionTrace, TraceEvent, VisualizationState
│       │   ├── TraceBuilder           accumulates steps while running
│       │   ├── Highlighter            touches → cell colours
│       │   └── SwapDetector           collapses the temp-variable swap
│       ├── service/                   Execution, Credit, History, SavedCode, Dashboard, ai/
│       ├── controller/ dto/ entity/ repository/ security/ config/ exception/
│       └── resources/db/migration/    Flyway (portable across Postgres and H2)
├── frontend/
│   └── src/
│       ├── types.ts                   the contract, mirrored
│       ├── hooks/usePlayer.ts          playback engine
│       ├── components/visualizer/      Array, Matrix, Variables, CallStack,
│       │                               Timeline, Playback, Metrics, Output, Ai
│       └── pages/                      Visualizer, Problems, Dashboard, History, ...
├── docs/                              ARCHITECTURE, SUPPORTED_JAVA_SUBSET, API, ROADMAP
└── docker-compose.yml
```

---

## API

Full interactive reference at `/swagger-ui.html`. Details in [`docs/API.md`](docs/API.md).

| Method | Path | Auth | Purpose |
|---|---|---|---|
| `GET` | `/api/meta` | — | Capabilities, limits, prices |
| `POST` | `/api/auth/register` | — | Create an account |
| `POST` | `/api/auth/login` | — | Get a token |
| `GET` | `/api/auth/me` | ✓ | Current user |
| `POST` | `/api/executions` | optional | **Run code, get a trace** |
| `GET` | `/api/executions` | ✓ | Run history |
| `GET` | `/api/executions/{id}` | ✓ | Replay a stored run |
| `DELETE` | `/api/executions/{id}` | ✓ | Delete a run |
| `GET` | `/api/problems` | — | Problem library |
| `GET` | `/api/problems/{slug}` | — | One problem with starter code |
| `GET/POST/PUT/DELETE` | `/api/saved-code` | ✓ | Saved snippets |
| `GET` | `/api/credits` | ✓ | Balance and price list |
| `GET` | `/api/credits/transactions` | ✓ | Credit ledger |
| `GET` | `/api/dashboard` | ✓ | Counts and recent activity |
| `POST` | `/api/ai/explain` | ✓ | AI explanation |

Send `Authorization: Bearer <token>`. Every error uses one shape:

```json
{
  "timestamp": "2026-09-09T18:30:00Z",
  "status": 400,
  "error": "VALIDATION_FAILED",
  "message": "Some fields are invalid",
  "path": "/api/auth/register",
  "fieldErrors": { "email": "Enter a valid email address" }
}
```

### Credits

A ledger, not a counter. Every balance change writes a `CreditTransaction` row with a reason and
the resulting balance, in the same database transaction — so the ledger always explains the
number on screen.

| Action | Cost |
|---|---|
| Run code | 1 |
| AI explanation | 5 |
| AI bug analysis | 10 |
| Signup grant | +100 |
| Monthly grant | +100 |

Two deliberate choices:

- **Metering and enforcement are separate.** With `ALGOLENS_CREDITS_ENFORCED=false` (the default)
  usage is recorded but nothing is ever refused, so prices can be tuned against real usage before
  they gate anyone.
- **A syntax error costs nothing.** Charging for a typo teaches people to stop pressing Run.
  A failed *local* AI answer is free too — that would be charging for our own outage.

---

## What Java is supported

Enough to write essentially any algorithm from a DSA course — arrays, recursion, linked lists,
trees, and graph traversals using real collections. Paste any of these shapes:

```java
// 1. bare statements — no class, no main
int[] arr = {5, 2, 8, 1};
for (int i = 0; i < arr.length; i++) { ... }
```

```java
// 2. a full class with main
public class Main {
    static int factorial(int n) { ... }
    public static void main(String[] args) { ... }
}
```

```java
// 3. statements plus helper methods
static int square(int n) { return n * n; }
int result = square(7);
```

```java
// 4. classes — nested or top level, so linked lists and trees work
public class Main {
    static class Node {
        int data;
        Node next;
        Node(int data) { this.data = data; }
    }

    public static void main(String[] args) {
        Node head = new Node(1);
        head.next = new Node(2);
    }
}
```

```java
// 5. collections, generics, switch, try/catch — ordinary everyday Java
import java.util.*;

Map<String, Integer> counts = new HashMap<>();
for (String word : new String[] {"a", "b", "a"}) {
    counts.put(word, counts.getOrDefault(word, 0) + 1);
}

Deque<Integer> stack = new ArrayDeque<>();
stack.push(1);

try {
    System.out.println(counts + " " + stack.pop());
} catch (Exception e) {
    System.out.println(e.getMessage());
}
```

**Types** — `int` `long` `double` `boolean` `char` `String`, their arrays (including 2D), and
your own classes.

**Control flow** — `for`, enhanced `for`, `while`, `do-while`, `if`/`else`, `switch` (with real
fall-through *and* the arrow form), `break`, `continue`, `return`, `try`/`catch`/`finally`,
`throw`.

**Objects** — classes (nested or top level), constructors, instance methods, `this`, fields,
`new`, reference equality, `null`, `instanceof`, and overloading by argument count.

**Collections** — `ArrayList` `LinkedList` `Stack` `ArrayDeque` `PriorityQueue` `HashMap`
`LinkedHashMap` `TreeMap` `HashSet` `LinkedHashSet` `TreeSet` `StringBuilder`, with generics
(erased) including nested `Map<String, List<Integer>>` and the diamond.

**Library** — `Math.*`, `Arrays.*` (including `sort` and `binarySearch`), `Collections.*`,
`String` methods, `String.format`, `System.out.printf`, the wrapper statics.

Not supported yet: **lambdas and streams** (the largest remaining gap), inheritance and
interfaces, custom `Comparator`s, explicit `Iterator`s, labelled breaks.

Integer semantics match `javac` exactly, including `7 / 2 == 3` and `Integer.MAX_VALUE + 1`
wrapping to `-2147483648`, and narrowing without a cast is rejected the same way. That matters:
a student debugging a real overflow must not find the visualizer disagreeing with the compiler.

Full list, with the reasoning behind each exclusion:
[`docs/SUPPORTED_JAVA_SUBSET.md`](docs/SUPPORTED_JAVA_SUBSET.md).

---

## Roadmap

Shipped:

- [x] Java-subset interpreter with step-level tracing
- [x] Array and matrix visualization with pointers and cell highlighting
- [x] **Object model** — classes, constructors, `this`, fields, `new`, reference identity
- [x] **Linked list, tree and object-graph visualization**, cycles included
- [x] **Collections** — lists, stacks, queues, deques, heaps, maps, sets, `StringBuilder`,
      with generics; maps and sets get their own visualizations
- [x] **`switch`, `try`/`catch`/`finally`, `throw`, `instanceof`**
- [x] Play / pause / step / scrub / speed control, forwards and backwards
- [x] Variables, call stack, output, run metrics
- [x] JWT auth, saved code, replayable run history, dashboard
- [x] Credit ledger with separate metering and enforcement
- [x] AI explanations, with a working offline fallback
- [x] 18-problem starter library across arrays, searching, sorting, recursion, linked lists
      and trees
- [x] 165 tests; Docker for the full stack

Next, in order:

1. **Lambdas** — the largest remaining gap. Unblocks `computeIfAbsent`, `forEach`, `removeIf`,
   custom `Comparator`s and streams.
2. **Delta-encoded traces**, so a 10,000-step run is not tens of megabytes of JSON.
3. **Graph-specific views** — highlighting the frontier and visited set during BFS/Dijkstra.
4. **Python**, then **C++**, behind the existing `CodeExecutor` interface.
5. Payments, and only then enforce credits.

[`docs/ROADMAP.md`](docs/ROADMAP.md) has the detail and the reasoning about sequencing.

---

## Troubleshooting

**`./mvnw` says permission denied** — `chmod +x mvnw`. On Windows use `.\mvnw.cmd`.

**Backend exits with "algolens.jwt.secret must be at least 32 bytes"** — working as intended.
Set `ALGOLENS_JWT_SECRET` to something longer; `openssl rand -base64 48` generates one.

**Frontend loads but every action fails with "Could not reach the AlgoLens backend"** — the
backend is not on port 8080. Check terminal 1, then `curl http://localhost:8080/api/meta/health`.

**Port 8080 already in use** — `SERVER_PORT=8081 ./mvnw spring-boot:run`, then start the frontend
with `VITE_API_TARGET=http://localhost:8081 npm run dev`.

**The editor area is blank** — Monaco is fetched from a CDN on first load, so the editor needs
network access the first time. Everything else works offline.

**"... is not in the supported subset yet"** — that message names exactly what is missing.
Lambdas are the usual one; rewrite the call as a loop. See
[`docs/SUPPORTED_JAVA_SUBSET.md`](docs/SUPPORTED_JAVA_SUBSET.md).

**How do I look at the database?** Start the backend and open
<http://localhost:8080/h2-console>, with JDBC URL `jdbc:h2:file:./data/algolens`, user `sa`, no
password. PostgreSQL is not required. Full guide: [`docs/DATABASE.md`](docs/DATABASE.md).

**A run says LIMIT_EXCEEDED** — the input is too large or there is an infinite loop. The partial
trace up to that point is still returned and still steppable.

**Reset the dev database** — stop the backend and delete `backend/data/`. Flyway recreates the
schema and the problem library on the next start.

---

## Licence

MIT.
