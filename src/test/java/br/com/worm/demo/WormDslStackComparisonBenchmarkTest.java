package br.com.worm.demo;

import br.com.liviacare.worm.api.Persistable;
import br.com.liviacare.worm.query.FilterBuilder;
import br.com.liviacare.worm.query.Pageable;
import br.com.worm.demo.dto.BookProjection;
import br.com.worm.demo.dto.BookProjection_;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.FileWriter;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
public class WormDslStackComparisonBenchmarkTest {

    private static final DateTimeFormatter FILE_TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final int PAGE_SIZE = 20;
    private static final int PAGE_CYCLES = 8;
    private static final String NATIVE_BOOK_PROJECTION_SELECT = """
            SELECT id, title, isbn, status, author_id, created_at, updated_at, active
            FROM books
            """;

    private static final int ROUNDS = intProp("benchmark.compare.rounds", 3);
    private static final int OPS_SCALE = intProp("benchmark.compare.ops.scale", 1);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static int intProp(String key, int defaultValue) {
        String val = System.getProperty(key);
        if (val == null || val.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    @AfterEach
    void cleanupTables() {
        jdbcTemplate.update("DELETE FROM books");
        jdbcTemplate.update("DELETE FROM authors");
    }

    @Test
    void runAndExportNativeWormAndWormDslComparison() throws IOException {
        LinkedHashMap<String, Integer> opsPerScenario = new LinkedHashMap<>();
        opsPerScenario.put("selectById", 500 * OPS_SCALE);
        opsPerScenario.put("selectPageByStatusSorted", 300 * OPS_SCALE);
        ProgressBar progressBar = new ProgressBar(opsPerScenario.size() * 3 * ROUNDS);

        List<String> lines = new ArrayList<>();
        lines.add("scenario,stack,avg_us,p95_us,throughput_ops_per_sec,effective_ops_per_round,rows,checksum");

        for (var entry : opsPerScenario.entrySet()) {
            String scenario = entry.getKey();
            int ops = entry.getValue();

            ScenarioData data = seedScenarioData(Math.max(ops * 2, PAGE_SIZE * PAGE_CYCLES * 3));
            List<UUID> probes = buildProbes(data.bookIds(), ops);

            Result nativeResult;
            Result wormResult;
            Result wormDslResult;

            if ("selectById".equals(scenario)) {
                nativeResult = measure(scenario, "NATIVE_SQL", ops, ROUNDS, () -> runNativeSelectById(probes), progressBar);
                wormResult = measure(scenario, "WORM", ops, ROUNDS, () -> runWormSelectById(probes), progressBar);
                wormDslResult = measure(scenario, "WORM_DSL", ops, ROUNDS, () -> runWormDslSelectById(probes), progressBar);
            } else {
                nativeResult = measure(scenario, "NATIVE_SQL", ops, ROUNDS, () -> runNativePageByStatus(ops, data.status()), progressBar);
                wormResult = measure(scenario, "WORM", ops, ROUNDS, () -> runWormPageByStatus(ops, data.status()), progressBar);
                wormDslResult = measure(scenario, "WORM_DSL", ops, ROUNDS, () -> runWormDslPageByStatus(ops, data.status()), progressBar);
            }

            assertEquals(nativeResult.rows(), wormResult.rows(), scenario + " rows mismatch between native and WORM");
            assertEquals(nativeResult.checksum(), wormResult.checksum(), scenario + " checksum mismatch between native and WORM");
            assertEquals(nativeResult.rows(), wormDslResult.rows(), scenario + " rows mismatch between native and WORM-dsl");
            assertEquals(nativeResult.checksum(), wormDslResult.checksum(), scenario + " checksum mismatch between native and WORM-dsl");

            lines.add(format(scenario, "NATIVE_SQL", nativeResult));
            lines.add(format(scenario, "WORM", wormResult));
            lines.add(format(scenario, "WORM_DSL", wormDslResult));

            cleanupTables();
        }
        progressBar.complete();

        String out = String.format(
                Locale.ROOT,
                "WormDslStackComparisonBenchmark_%s.csv",
                LocalDateTime.now().format(FILE_TS_FORMAT)
        );
        try (FileWriter fw = new FileWriter(out)) {
            for (String line : lines) {
                fw.write(line);
                fw.write(System.lineSeparator());
            }
        }

        assertTrue(lines.size() > 1);
        System.out.println("CSV written to: " + out);
    }

    private String format(String scenario, String stack, Result r) {
        return String.format(
                Locale.ROOT,
                "%s,%s,%.2f,%.2f,%.2f,%d,%d,%d",
                scenario,
                stack,
                r.avgUs(),
                r.p95Us(),
                r.throughputOpsPerSec(),
                r.effectiveOpsPerRound(),
                r.rows(),
                r.checksum()
        );
    }

    private Result measure(String scenario, String stack, int ops, int rounds, ScenarioRunner runner, ProgressBar progressBar) {
        List<Double> perOpUs = new ArrayList<>();
        List<Double> tps = new ArrayList<>();
        RunOutcome reference = null;

        for (int round = 0; round < rounds; round++) {
            long startNs = System.nanoTime();
            RunOutcome outcome = runner.run();
            long durationMs = Math.max(1L, (System.nanoTime() - startNs) / 1_000_000L);

            if (reference == null) {
                reference = outcome;
            } else {
                assertEquals(reference.rows(), outcome.rows(), "Rows changed between rounds");
                assertEquals(reference.checksum(), outcome.checksum(), "Checksum changed between rounds");
            }

            perOpUs.add(durationMs * 1000.0 / Math.max(1, ops));
            tps.add(ops / (durationMs / 1000.0 + 1e-9));
            progressBar.step(scenario, stack, round + 1, rounds);
        }

        double avg = perOpUs.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        List<Double> sorted = perOpUs.stream().sorted().toList();
        int p95Idx = Math.max(0, Math.min(sorted.size() - 1, (int) Math.ceil(0.95 * sorted.size()) - 1));
        double p95 = sorted.get(p95Idx);
        double throughput = tps.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        RunOutcome finalOutcome = reference == null ? new RunOutcome(0, 0L) : reference;
        return new Result(avg, p95, throughput, ops, finalOutcome.rows(), finalOutcome.checksum());
    }

    private RunOutcome runNativeSelectById(List<UUID> probes) {
        long checksum = 0L;
        int rows = 0;
        for (UUID id : probes) {
            List<BookProjection> found = jdbcTemplate.query(
                    NATIVE_BOOK_PROJECTION_SELECT + " WHERE id = ? AND active = TRUE ORDER BY id LIMIT 1",
                    this::mapBookProjection,
                    id
            );
            if (!found.isEmpty()) {
                rows++;
                checksum = mix(checksum, found.getFirst().id());
            }
        }
        return new RunOutcome(rows, checksum);
    }

    private RunOutcome runWormSelectById(List<UUID> probes) {
        long checksum = 0L;
        int rows = 0;
        for (UUID id : probes) {
            Optional<BookProjection> found = BookProjection.find.one(
                    FilterBuilder.create()
                            .notJoin()
                            .eq("id", id)
                            .eq("active", true)
            );
            if (found.isPresent()) {
                rows++;
                checksum = mix(checksum, found.orElseThrow().id());
            }
        }
        return new RunOutcome(rows, checksum);
    }

    private RunOutcome runWormDslSelectById(List<UUID> probes) {
        long checksum = 0L;
        int rows = 0;
        for (UUID id : probes) {
            Optional<BookProjection> found = BookProjection.find.one(
                    FilterBuilder.create()
                            .notJoin()
                            .eq(BookProjection_.id, id)
                            .eq(BookProjection_.active, true)
            );
            if (found.isPresent()) {
                rows++;
                checksum = mix(checksum, found.orElseThrow().id());
            }
        }
        return new RunOutcome(rows, checksum);
    }

    private RunOutcome runNativePageByStatus(int ops, String status) {
        long checksum = 0L;
        int rows = 0;
        for (int i = 0; i < ops; i++) {
            int page = i % PAGE_CYCLES;
            int offset = page * PAGE_SIZE;
            List<BookProjection> books = jdbcTemplate.query(
                    NATIVE_BOOK_PROJECTION_SELECT + " WHERE status = ? AND active = TRUE ORDER BY title ASC LIMIT ? OFFSET ?",
                    this::mapBookProjection,
                    status, PAGE_SIZE, offset
            );
            rows += books.size();
            for (BookProjection book : books) {
                checksum = mix(checksum, book.id());
            }
        }
        return new RunOutcome(rows, checksum);
    }

    private RunOutcome runWormPageByStatus(int ops, String status) {
        long checksum = 0L;
        int rows = 0;
        for (int i = 0; i < ops; i++) {
            int page = i % PAGE_CYCLES;
            var pageable = Pageable.of(page, PAGE_SIZE, Pageable.Sort.asc("title"));
            var slice = BookProjection.find.all(
                    FilterBuilder.create()
                            .notJoin()
                            .eq("status", status)
                            .eq("active", true),
                    pageable
            );
            rows += slice.content().size();
            for (BookProjection book : slice.content()) {
                checksum = mix(checksum, book.id());
            }
        }
        return new RunOutcome(rows, checksum);
    }

    private RunOutcome runWormDslPageByStatus(int ops, String status) {
        long checksum = 0L;
        int rows = 0;
        for (int i = 0; i < ops; i++) {
            int page = i % PAGE_CYCLES;
            var pageable = Pageable.of(page, PAGE_SIZE, Pageable.Sort.asc("title"));
            var slice = BookProjection.find.all(
                    FilterBuilder.create()
                            .notJoin()
                            .eq(BookProjection_.status, status)
                            .eq(BookProjection_.active, true),
                    pageable
            );
            rows += slice.content().size();
            for (BookProjection book : slice.content()) {
                checksum = mix(checksum, book.id());
            }
        }
        return new RunOutcome(rows, checksum);
    }

    private ScenarioData seedScenarioData(int totalBooks) {
        cleanupTables();

        int authorCount = Math.max(20, totalBooks / 10);
        String runTag = UUID.randomUUID().toString();

        List<Author> authors = new ArrayList<>(authorCount);
        for (int i = 0; i < authorCount; i++) {
            authors.add(Author.builder()
                    .id(UUID.randomUUID())
                    .name("Author " + i)
                    .email("author-" + i + "-" + runTag + "@x.com")
                    .build());
        }
        Persistable.saveAll(authors);

        List<Book> books = new ArrayList<>(totalBooks);
        List<UUID> ids = new ArrayList<>(totalBooks);
        for (int i = 0; i < totalBooks; i++) {
            Author author = authors.get(i % authorCount);
            Book book = Book.builder()
                    .id(UUID.randomUUID())
                    .title(String.format(Locale.ROOT, "Book %06d", i))
                    .isbn("isbn-" + i + "-" + runTag)
                    .status((i % 2 == 0) ? "AVAILABLE" : "DRAFT")
                    .authorId(author.getId())
                    .active(true)
                    .build();
            books.add(book);
            ids.add(book.getId());
        }
        Persistable.saveAll(books);
        return new ScenarioData(ids, "AVAILABLE");
    }

    private List<UUID> buildProbes(List<UUID> ids, int ops) {
        List<UUID> out = new ArrayList<>(ops);
        int size = Math.max(1, ids.size());
        for (int i = 0; i < ops; i++) {
            out.add(ids.get(i % size));
        }
        return out;
    }

    private long mix(long checksum, UUID id) {
        return checksum * 31L + id.hashCode();
    }

    private BookProjection mapBookProjection(ResultSet rs, int rowNum) throws SQLException {
        return new BookProjection(
                rs.getObject("id", UUID.class),
                rs.getString("title"),
                rs.getString("isbn"),
                rs.getString("status"),
                rs.getObject("author_id", UUID.class),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("updated_at", LocalDateTime.class),
                rs.getBoolean("active")
        );
    }

    private interface ScenarioRunner {
        RunOutcome run();
    }

    private static final class ProgressBar {
        private static final int WIDTH = 28;
        private final int totalSteps;
        private int currentStep;

        private ProgressBar(int totalSteps) {
            this.totalSteps = Math.max(1, totalSteps);
        }

        private void step(String scenario, String stack, int round, int rounds) {
            currentStep++;
            printLine(scenario, stack, round, rounds);
        }

        private void complete() {
            if (currentStep < totalSteps) {
                currentStep = totalSteps;
            }
            printLine("finalizando", "CSV", 1, 1);
            System.out.println();
        }

        private void printLine(String scenario, String stack, int round, int rounds) {
            int filled = (int) Math.round((double) currentStep / totalSteps * WIDTH);
            if (filled > WIDTH) {
                filled = WIDTH;
            }
            int remaining = WIDTH - filled;
            double percent = (100.0 * currentStep) / totalSteps;
            String bar = "[" + "#".repeat(filled) + "-".repeat(remaining) + "]";
            String message = String.format(
                    Locale.ROOT,
                    "\r%s %6.2f%% (%d/%d) | %s | %s | round %d/%d",
                    bar,
                    percent,
                    currentStep,
                    totalSteps,
                    scenario,
                    stack,
                    round,
                    rounds
            );
            System.out.print(message);
        }
    }

    private record ScenarioData(List<UUID> bookIds, String status) {}
    private record RunOutcome(int rows, long checksum) {}
    private record Result(double avgUs, double p95Us, double throughputOpsPerSec, int effectiveOpsPerRound, int rows, long checksum) {}
}
