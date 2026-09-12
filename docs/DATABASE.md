# Looking at the database

## Short answer

**PostgreSQL is not installed on this machine, and you do not need it.** AlgoLens runs on
embedded **H2** by default, and a browser-based DB viewer is already built in:

1. Start the backend (`cd backend` then `.\mvnw.cmd spring-boot:run`)
2. Open <http://localhost:8080/h2-console>
3. Fill in exactly this:

   | Field | Value |
   |---|---|
   | Saved Settings | `Generic H2 (Embedded)` |
   | **JDBC URL** | `jdbc:h2:file:./data/algolens` |
   | User Name | `sa` |
   | Password | *(leave empty)* |

4. Click **Connect**

The tables appear in the left sidebar. Type SQL in the box and press **Run**.

> The JDBC URL is the one field people get wrong — the default in the box is
> `jdbc:h2:~/test`, which creates an empty unrelated database. Replace it with the value above.

### Where the file actually is

`backend/data/algolens.mv.db`. It is a single file, git-ignored, and safe to delete: Flyway
recreates the schema and reseeds the problem library on the next start. That is the quickest way
to reset everything:

```powershell
# stop the backend first, or Windows will refuse - the file is locked while it runs
Remove-Item -Recurse -Force backend\data
```

### One thing to know about H2

The console needs its **own** connection to the file, and embedded H2 allows only one process at
a time. So it works while the backend is running (the console runs *inside* the backend), but a
desktop tool like DBeaver cannot open the same file at the same time. Stop the backend first if
you want to use one.

---

## Useful queries for this schema

```sql
-- Everything at a glance
SELECT * FROM users;
SELECT * FROM problems ORDER BY display_order;

-- Run history, newest first. trace_json is large, so it is excluded here.
SELECT id, user_id, language, status, total_steps, duration_ms, credits_spent, created_at
FROM execution_history
ORDER BY created_at DESC
LIMIT 20;

-- The credit ledger for one user, and proof it adds up
SELECT amount, type, description, balance_after, created_at
FROM credit_transactions
WHERE user_id = 1
ORDER BY created_at DESC;

SELECT u.email,
       u.credit_balance                AS cached_balance,
       COALESCE(SUM(t.amount), 0)      AS ledger_total
FROM users u
LEFT JOIN credit_transactions t ON t.user_id = u.id
GROUP BY u.id, u.email, u.credit_balance;
-- cached_balance and ledger_total must always match. If they ever diverge,
-- something bypassed CreditService.apply(), which is the one place allowed to move a balance.

-- Saved snippets, without dumping the code column
SELECT id, user_id, title, language, LENGTH(code) AS code_chars, updated_at
FROM saved_code
ORDER BY updated_at DESC;

-- How big are the stored traces?
SELECT id, total_steps, LENGTH(trace_json) AS trace_bytes
FROM execution_history
WHERE trace_json IS NOT NULL
ORDER BY trace_bytes DESC
LIMIT 10;

-- Which Flyway migrations have run
SELECT installed_rank, version, description, success FROM flyway_schema_history;
```

---

## If you *do* want PostgreSQL

Nothing in the app needs it — the same Flyway migrations run on both, and every test passes on
H2. Use Postgres when you want the production-shaped setup, multiple processes reading the
database at once, or a real GUI client.

### Option A — Docker (easiest, no install)

Docker is also not installed here, but if you add it:

```powershell
docker compose up -d db
```

That starts Postgres 16 on `localhost:5432` with database/user/password `algolens`.

### Option B — install it natively

```powershell
winget install PostgreSQL.PostgreSQL.16
```

The installer bundles **pgAdmin 4**, which is a full GUI client — nicer than the H2 console.
After installing, `psql` lives at
`C:\Program Files\PostgreSQL\16\bin\psql.exe` and is usually not on `PATH`; either add that
`bin` folder to `PATH` or call it by full path.

Then create the database:

```powershell
& "C:\Program Files\PostgreSQL\16\bin\psql.exe" -U postgres -c "CREATE DATABASE algolens;"
& "C:\Program Files\PostgreSQL\16\bin\psql.exe" -U postgres -c "CREATE USER algolens WITH PASSWORD 'algolens';"
& "C:\Program Files\PostgreSQL\16\bin\psql.exe" -U postgres -c "GRANT ALL PRIVILEGES ON DATABASE algolens TO algolens;"
```

### Then run the backend against it

```powershell
$env:SPRING_PROFILES_ACTIVE = "postgres"
cd backend
.\mvnw.cmd spring-boot:run
```

Flyway creates the tables on first start and `ProblemSeeder` fills the problem library.

Connect with:

```powershell
& "C:\Program Files\PostgreSQL\16\bin\psql.exe" -U algolens -d algolens
```

Handy `psql` commands: `\dt` lists tables, `\d users` describes one, `\x` toggles readable
column-per-line output (worth it for `execution_history`), `\q` quits.

Override the connection with `ALGOLENS_DB_URL`, `ALGOLENS_DB_USER` and `ALGOLENS_DB_PASSWORD`
if your setup differs from the defaults.

---

## The schema

Five tables. Full column definitions in
[`backend/src/main/resources/db/migration/V1__init.sql`](../backend/src/main/resources/db/migration/V1__init.sql).

```
users ───┬──< saved_code ───┐
         │                  │
         ├──< execution_history >─── problems
         │
         └──< credit_transactions
```

| Table | What it holds |
|---|---|
| `users` | Accounts, BCrypt password hashes, cached credit balance |
| `problems` | The starter library, seeded on first boot |
| `saved_code` | Snippets a user saved |
| `execution_history` | One row per run, including `trace_json` for replay |
| `credit_transactions` | Append-only ledger; every balance change writes one row |

`execution_history.trace_json` is the whole serialised trace, which is what makes opening a past
run replay the identical execution without re-running anything. It is left `NULL` when a trace
exceeded `algolens.execution.max-persisted-trace-bytes`, and
`ExecutionSummaryResponse.replayable` tells the UI which is which.
