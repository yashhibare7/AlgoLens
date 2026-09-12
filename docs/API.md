# API reference

Base URL `http://localhost:8080`. Interactive docs at `/swagger-ui.html`, schema at
`/v3/api-docs`.

Authenticate with `Authorization: Bearer <token>` from `/api/auth/login`.

---

## Error shape

Every non-2xx response, from every endpoint:

```json
{
  "timestamp": "2026-09-09T18:30:00Z",
  "status": 400,
  "error": "VALIDATION_FAILED",
  "message": "Some fields are invalid",
  "path": "/api/auth/register",
  "fieldErrors": { "email": "Enter a valid email address" },
  "details": null
}
```

| `error` | Status | Meaning |
|---|---|---|
| `VALIDATION_FAILED` | 400 | Check `fieldErrors` |
| `BAD_REQUEST` | 400 | Unknown language, bad parameter |
| `MALFORMED_REQUEST` | 400 | Body could not be parsed |
| `UNAUTHENTICATED` | 401 | Missing, expired or forged token |
| `INVALID_CREDENTIALS` | 401 | Wrong email **or** password — deliberately not distinguished |
| `FORBIDDEN` | 403 | Authenticated, not allowed |
| `NOT_FOUND` | 404 | Also returned for another user's resource, on purpose |
| `INSUFFICIENT_CREDITS` | 402 | `details.required` and `details.available` |
| `CONFLICT` | 409 | Email already registered |
| `EXECUTION_TIMEOUT` | 408 | Sandbox backstop fired |
| `INTERNAL_ERROR` | 500 | Logged server-side; the message is deliberately generic |

**A failed run is not an error.** `POST /api/executions` answers `200` with a trace whose
`status` is `COMPILE_ERROR`, `RUNTIME_ERROR`, `TIMEOUT` or `LIMIT_EXCEEDED`. The steps that ran
before the failure are the useful part, so they are returned rather than discarded.

---

## `GET /api/meta`

Public. Read this at boot instead of hardcoding capabilities.

```json
{
  "version": "0.1.0",
  "languages": [
    { "id": "JAVA", "displayName": "Java", "supported": true },
    { "id": "PYTHON", "displayName": "Python", "supported": false },
    { "id": "CPP", "displayName": "C++", "supported": false },
    { "id": "JAVASCRIPT", "displayName": "JavaScript", "supported": false }
  ],
  "limits": {
    "maxCodeLength": 20000, "maxSteps": 200000, "maxTraceEvents": 20000,
    "wallClockTimeoutMs": 5000, "maxArrayLength": 10000, "maxCallDepth": 200
  },
  "creditCosts": { "CODE_EXECUTION": 1, "AI_EXPLANATION": 5, "AI_ANALYSIS": 10 },
  "creditsEnforced": false,
  "aiEnabled": true,
  "aiProvider": "heuristic",
  "anonymousExecutionAllowed": true
}
```

`aiProvider` is `"claude"` when a key is configured and `"heuristic"` otherwise, so the UI can be
honest about which the user is reading.

`GET /api/meta/health` → `{"status":"UP","service":"algolens-backend"}`.

---

## Authentication

### `POST /api/auth/register` → 201

```json
{ "name": "Ada", "email": "ada@example.com", "password": "at-least-8-chars" }
```

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresInSeconds": 86400,
  "user": { "id": 1, "name": "Ada", "email": "ada@example.com",
            "role": "USER", "creditBalance": 100, "createdAt": "..." }
}
```

The signup grant is applied and ledgered in the same transaction as the account.

### `POST /api/auth/login` → 200
`{ "email": "...", "password": "..." }` → same shape.

### `GET /api/auth/me` → 200 🔒

---

## `POST /api/executions` — the important one

Auth **optional** while `anonymousExecutionAllowed` is true. Anonymous runs are not recorded and
cost nothing.

```json
{
  "language": "JAVA",
  "code": "int[] arr = {2, 1};\nint t = arr[0];\narr[0] = arr[1];\narr[1] = t;\n",
  "problemId": null,
  "savedCodeId": null
}
```

`problemId` and `savedCodeId` are optional back-references used by history and progress tracking.

### Response

```json
{
  "executionId": 42,
  "creditsSpent": 1,
  "creditBalance": 99,
  "trace": {
    "language": "JAVA",
    "status": "SUCCESS",
    "totalSteps": 6,
    "truncated": false,
    "stdout": "",
    "errorMessage": null,
    "errorLine": null,
    "durationMs": 3,
    "metrics": {
      "statements": 6, "comparisons": 0, "swaps": 1,
      "arrayReads": 2, "arrayWrites": 2,
      "fieldReads": 0, "fieldWrites": 0, "objectsCreated": 0,
      "calls": 0, "maxCallDepth": 0
    },
    "steps": [ /* TraceEvent[] */ ]
  }
}
```

`executionId` and `creditBalance` are absent for anonymous runs.

### `TraceEvent`

```json
{
  "step": 4,
  "line": 4,
  "action": "SWAP",
  "message": "Swapped arr[0] and arr[1] -> 1 and 2",
  "depth": 0,
  "variables": [
    { "name": "t", "type": "int", "value": 2, "changed": false }
  ],
  "visualizations": [
    {
      "type": "ARRAY",
      "name": "arr",
      "elementType": "int",
      "elements": [
        { "index": 0, "value": 1, "state": "SWAPPING" },
        { "index": 1, "value": 2, "state": "SWAPPING" }
      ],
      "pointers": [{ "name": "i", "index": 0 }]
    }
  ],
  "callStack": null,
  "touches": [
    { "target": "arr", "index": 0, "kind": "SWAP", "value": 1 },
    { "target": "arr", "index": 1, "kind": "SWAP", "value": 2 }
  ],
  "output": null
}
```

Field notes that matter when consuming this:

- **`line`** is 1-based and is the line that just finished. Highlight it.
- **`action`** — `START` `STATEMENT` `DECLARE` `ASSIGN` `ARRAY_READ` `ARRAY_WRITE` `FIELD_READ`
  `FIELD_WRITE` `ALLOCATE` `COMPARE` `SWAP` `CONDITION` `CALL` `RETURN` `OUTPUT` `DONE` `ERROR`.
  Step 0 is always `START`; the last step is `DONE` or `ERROR`.
- **`state`** — `DEFAULT` `ACTIVE` `COMPARING` `SWAPPING` `WRITTEN` `SORTED` `PIVOT` `FOUND`
  `EXCLUDED`. Already computed server-side from `touches`; do not recompute it.
- **`visualizations[].type`** — `ARRAY` `MATRIX` `MAP` `SET` `LINKED_LIST` `STACK` `QUEUE`
  `TREE` `GRAPH`. Which payload field is populated follows from it: `elements` for the row
  shapes (`ARRAY`/`SET`/`STACK`/`QUEUE`), `rows` for `MATRIX`, `entries` for `MAP`, and
  `nodes`/`edges` for the reference shapes.
- **`entries[]`** (maps only) is `{index, key, value, state}`. `index` is the pair's position in
  iteration order, which is how a touch addresses and highlights it.
- **`output`** is this step's output **delta**, not the whole buffer. Accumulate it as you step so
  output stays in sync with the playhead. The complete text is `trace.stdout`.
- **`callStack`** is omitted entirely while only the entry frame is active — it would otherwise
  duplicate `variables` on every one of thousands of steps.
- **`visualizations`** is omitted when nothing drawable is in scope.
- **`metrics.comparisons`** counts comparisons of *data* only — an array cell or an object
  reference on at least one side. `arr[j] > arr[j+1]` and `slow == fast` count;
  `i < arr.length` does not.
- Nulls are omitted throughout, so treat absent and null as the same thing.

### Reference structures

A `visualizations` entry with `nodes` instead of `elements` is a reference-linked structure.
Linked lists, trees and general object graphs are the **same payload** — only `type` differs,
telling the client how to lay it out.

```json
{
  "type": "LINKED_LIST",
  "name": "head",
  "elementType": "Node",
  "nodes": [
    {
      "id": "Node@1",
      "className": "Node",
      "value": 1,
      "fields": [
        { "name": "data", "type": "int",  "value": 1,        "changed": false },
        { "name": "next", "type": "Node", "value": "Node@2", "changed": false }
      ],
      "state": "DEFAULT"
    }
  ],
  "edges": [
    { "from": "Node@1", "to": "Node@2", "label": "next" },
    { "from": "Node@4", "to": null,     "label": "next" }
  ],
  "pointers": [{ "name": "head", "nodeId": "Node@1" }]
}
```

- **`id`** is stable for the life of the object, so a client can keep a node in the same place
  across steps.
- **`value`** is the first non-reference field: the payload a learner reads off the node.
  `fields` has everything.
- **`edges[].to`** is `null` when the field is null — that is the list's terminator, sent
  explicitly rather than omitted so the tail can be drawn.
- **A cycle is an edge back to an existing `id`**, never an unrolled chain.
- **`type`** is chosen from the *names* of the reference fields: `next`/`prev` →
  `LINKED_LIST`, `left`/`right`/`parent` → `TREE`, anything else → `GRAPH`. Naming is more
  stable than runtime shape, since a tree with an empty right subtree is structurally a list.
- **`pointers[]`** carries `nodeId` for objects and `index` for array cells — exactly one of
  the two is present.

A structure reachable from two variables is **one** visualization with two pointers on it, not
two visualizations. Objects are grouped into connected components over the undirected reference
graph.

---

## History

| Endpoint | Notes |
|---|---|
| `GET /api/executions?page=0&size=20` 🔒 | Summaries. `replayable` is false when the trace was too large to store |
| `GET /api/executions/{id}` 🔒 | The original code plus the stored trace. Replays without re-running anything and costs no credits |
| `DELETE /api/executions/{id}` 🔒 | 204 |

Scoped by user id in the query, so another user's id returns 404 rather than 403 — no existence
oracle.

Paged responses:

```json
{ "content": [], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0, "last": true }
```

`size` is capped at 100.

---

## Problems

| Endpoint | Notes |
|---|---|
| `GET /api/problems[?category=Sorting]` | Public. Summaries |
| `GET /api/problems/{slugOrId}[?includeSolution=true]` | Public. Accepts a slug or a numeric id. `solutionCode` is only returned for signed-in callers |

## Saved code 🔒

`GET /api/saved-code` (titles only), `GET /api/saved-code/{id}` (with the body),
`POST /api/saved-code` → 201, `PUT /api/saved-code/{id}`, `DELETE /api/saved-code/{id}` → 204.

```json
{ "title": "My Bubble Sort", "language": "JAVA", "code": "...", "problemId": 7 }
```

## Credits 🔒

`GET /api/credits` → `{ "balance": 99, "enforced": false, "costs": { ... } }`

`GET /api/credits/transactions` → the ledger, newest first:

```json
{ "id": 2, "amount": -1, "type": "CODE_EXECUTION",
  "description": "Ran JAVA code (48 steps)", "balanceAfter": 99, "createdAt": "..." }
```

Types: `SIGNUP_GRANT` `MONTHLY_GRANT` `PURCHASE` `REFUND` `ADMIN_ADJUSTMENT` `CODE_EXECUTION`
`AI_EXPLANATION` `AI_ANALYSIS`. `amount` is signed and the entries sum to `balance`.

## `GET /api/dashboard` 🔒

```json
{
  "user": { },
  "totalExecutions": 12, "successfulExecutions": 9, "problemsSolved": 3,
  "savedSnippets": 2, "creditBalance": 88,
  "recentExecutions": []
}
```

---

## `POST /api/ai/explain` 🔒

```json
{
  "mode": "EXPLAIN_STEP",
  "language": "JAVA",
  "code": "int[] arr = {5, 2};\n...",
  "stepIndex": 9,
  "traceExcerpt": [
    "step 8 | line 5 | COMPARE | arr[0]=5 > arr[1]=2 -> true | arr=[5, 2] | i=0 j=0",
    "step 9 | line 8 | SWAP | Swapped arr[0] and arr[1] -> 2 and 5 | arr=[2, 5]"
  ],
  "question": "why does j stop one early?"
}
```

`mode` is `EXPLAIN_STEP` | `EXPLAIN_CODE` | `COMPLEXITY` | `FIND_BUG`.

**Why the client sends rendered trace lines rather than the trace.** It is roughly an order of
magnitude fewer tokens than the JSON, it is exactly what the model needs, and because the client
builds it the assistant also works for anonymous runs that were never persisted. Cap: 60 lines,
500 characters each. Put the current step last — the prompt tells the model to expect that.

```json
{
  "mode": "EXPLAIN_STEP",
  "explanation": "At this step `j` is 0, so...",
  "provider": "claude",
  "model": "claude-opus-5",
  "creditsSpent": 5,
  "creditBalance": 94
}
```

`provider` is `"heuristic"` when the local explainer answered — because no key is configured, or
because the API call failed. **Those answers are free**: charging for a fallback would be charging
for our own outage. Check `provider` before showing the answer as an AI explanation.

---

## curl walkthrough

```bash
# 1. Run bubble sort anonymously
curl -s -X POST http://localhost:8080/api/executions \
  -H 'Content-Type: application/json' \
  -d '{"language":"JAVA","code":"int[] a={3,1,2};\nfor(int i=0;i<a.length-1;i++){for(int j=0;j<a.length-1-i;j++){if(a[j]>a[j+1]){int t=a[j];a[j]=a[j+1];a[j+1]=t;}}}\n"}'

# 2. Register and keep the token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"name":"Ada","email":"ada@example.com","password":"correct-horse"}' \
  | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')

# 3. Run again, this time recorded
curl -s -X POST http://localhost:8080/api/executions \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"language":"JAVA","code":"int x = 1 + 1;\n"}'

# 4. History, and the ledger that explains the balance
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/executions
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/credits/transactions
```

🔒 = requires authentication.
