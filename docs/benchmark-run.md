# Running benchmarks against local PostgreSQL

This project contains a JUnit-based benchmark runner (`BenchmarkCliTest`) that executes WORM and JPA repository scenarios and exports a CSV to the **project root** with a timestamped filename:

```
BenchmarkCliTest_runAndExportBenchmark_<yyyyMMdd_HHmmss>.csv
```

---

## 1. Prepare local PostgreSQL

```bash
# create database
psql -U postgres -c "CREATE DATABASE worm_demo;"
# enable uuid extension
psql -U postgres -d worm_demo -c "CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\";"
```

Override connection settings with env vars if needed:

| Var | Default |
|-----|---------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/worm_demo` |
| `SPRING_DATASOURCE_USERNAME` | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | `postgres` |

---

## 2. Run the default benchmark (quick)

```bash
mvn -Dtest=BenchmarkCliTest#runAndExportBenchmark test
```

Default: **3 rounds**, ops per scenario as defined in the table below.

---

## 3. Run a longer / heavier benchmark

Two system properties control the run length — no code changes needed:

| Property | Default | Effect |
|----------|---------|--------|
| `benchmark.rounds` | `3` | Number of timed rounds per scenario |
| `benchmark.ops.scale` | `1` | Multiplier applied to all ops counts |

### Examples

**More rounds only** (better statistical accuracy, same data volume):
```bash
mvn -Dtest=BenchmarkCliTest#runAndExportBenchmark \
    -Dbenchmark.rounds=10 test
```

**More ops only** (larger data volume, same number of rounds):
```bash
mvn -Dtest=BenchmarkCliTest#runAndExportBenchmark \
    -Dbenchmark.ops.scale=5 test
```

**Both — production-like run** (10 rounds × 5× ops):
```bash
mvn -Dtest=BenchmarkCliTest#runAndExportBenchmark \
    -Dbenchmark.rounds=10 \
    -Dbenchmark.ops.scale=5 test
```

### Effective ops per scenario with `ops.scale=N`

| Scenario | Default ops | ×2 | ×5 | ×10 |
|----------|------------|----|----|-----|
| selectById | 500 | 1 000 | 2 500 | 5 000 |
| selectCountByStatus | 500 | 1 000 | 2 500 | 5 000 |
| selectPageByAuthor | 500 | 1 000 | 2 500 | 5 000 |
| selectJoinBookAuthor | 1 000 | 2 000 | 5 000 | 10 000 |
| insertSingle | 200 | 400 | 1 000 | 2 000 |
| insertBatch | 200 | 400 | 1 000 | 2 000 |
| updateSingle | 200 | 400 | 1 000 | 2 000 |
| updateBatch | 100 | 200 | 500 | 1 000 |
| deleteSingle | 200 | 400 | 1 000 | 2 000 |
| deleteBatch | 150 | 300 | 750 | 1 500 |

---

## 4. Open the CSV

```bash
# list the latest result
ls -t BenchmarkCliTest_runAndExportBenchmark_*.csv | head -1

# inspect it
column -s, -t $(ls -t BenchmarkCliTest_runAndExportBenchmark_*.csv | head -1)
```

---

## Notes

- **Logging**: all WORM, Hibernate and Spring logs are suppressed during tests (`application-test.yaml`) so they don't add I/O latency to benchmark timings.
- **Flyway**: disabled for tests; schema is initialised by `src/test/resources/schema.sql` via Spring SQL init.
- **Pagination**: `selectPageByAuthor` uses native WORM `FilterBuilder + Pageable` and JPA `Page<T>` — both hit the database, not in-memory filters.
- **Transaction isolation**: the WORM path runs without a wrapping test transaction; the JPA path uses a `JpaTransactionManager`-backed `TransactionTemplate` per scenario round.
