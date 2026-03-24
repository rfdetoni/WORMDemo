package br.com.worm.demo;

import br.com.liviacare.worm.api.Deletable;
import br.com.liviacare.worm.api.Persistable;
import br.com.liviacare.worm.query.FilterBuilder;
import br.com.liviacare.worm.query.Pageable;
import br.com.worm.demo.repository.AuthorRepository;
import br.com.worm.demo.repository.BookRepository;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
public class BenchmarkCliTest {

    private static final DateTimeFormatter FILE_TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final int PAGE_NUMBER = 0;
    private static final int PAGE_SIZE = 50;

    @Autowired
    private AuthorRepository authorRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private TransactionTemplate transactionTemplate;

    @PostConstruct
    void initTransactionTemplate() {
        this.transactionTemplate = new TransactionTemplate(new JpaTransactionManager(entityManagerFactory));
    }

    private static final int ROUNDS    = intProp("benchmark.rounds",    3);
    private static final int OPS_SCALE = intProp("benchmark.ops.scale", 1);

    private static final ThreadMXBean THREAD_MX = ManagementFactory.getThreadMXBean();

    private static int intProp(String key, int defaultValue) {
        String val = System.getProperty(key);
        if (val == null || val.isBlank()) return defaultValue;
        try { return Integer.parseInt(val.trim()); } catch (NumberFormatException e) { return defaultValue; }
    }

    private static long usedHeapMb() {
        Runtime rt = Runtime.getRuntime();
        return (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
    }

    private static long gcCollections() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(GarbageCollectorMXBean::getCollectionCount)
                .filter(c -> c >= 0).sum();
    }

    private static long gcTimeMs() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(GarbageCollectorMXBean::getCollectionTime)
                .filter(t -> t >= 0).sum();
    }

    private static long cpuSnapshot() {
        long t = THREAD_MX.getCurrentThreadCpuTime();
        return t < 0 ? 0 : t;
    }

    private record Result(double avgUs, double p95Us, double throughputOpsPerSec, int effectiveOpsPerRound,
                          double avgHeapDeltaMb, double avgCpuMs, double avgGcMs) {}

    @Test
    void runAndExportBenchmark() throws IOException {
        Map<String, Integer> opsPerScenario = new LinkedHashMap<>();
        opsPerScenario.put("selectById",          500  * OPS_SCALE);
        opsPerScenario.put("selectCountByStatus",  500  * OPS_SCALE);
        opsPerScenario.put("selectPageByAuthor",   500  * OPS_SCALE);
        opsPerScenario.put("selectJoinBookAuthor", 1000 * OPS_SCALE);
        opsPerScenario.put("insertSingle",         200  * OPS_SCALE);
        opsPerScenario.put("insertBatch",          200  * OPS_SCALE);
        opsPerScenario.put("updateSingle",         200  * OPS_SCALE);
        opsPerScenario.put("updateBatch",          100  * OPS_SCALE);
        opsPerScenario.put("deleteSingle",         200  * OPS_SCALE);
        opsPerScenario.put("deleteBatch",          150  * OPS_SCALE);

        List<String> header = List.of("scenario", "stack", "avg_us", "p95_us", "throughput_ops_per_sec",
                "effective_ops_per_round", "avg_heap_delta_mb", "avg_cpu_ms", "avg_gc_ms");
        List<String> lines = new ArrayList<>();
        lines.add(String.join(",", header));

        for (Map.Entry<String, Integer> e : opsPerScenario.entrySet()) {
            String scenario = e.getKey();
            int ops = e.getValue();

            Result worm = runWormLike(scenario, ops, ROUNDS);
            lines.add(format(scenario, "WORM", worm));

            Result jpa = runJpaLike(scenario, ops, ROUNDS);
            lines.add(format(scenario, "JPA", jpa));
        }

        String out = String.format(
                Locale.ROOT,
                "BenchmarkCliTest_runAndExportBenchmark_%s.csv",
                LocalDateTime.now().format(FILE_TS_FORMAT)
        );
        try (FileWriter fw = new FileWriter(out)) {
            for (String l : lines) {
                fw.write(l + System.lineSeparator());
            }
        }

        assertTrue(lines.size() > 1);
        System.out.println("CSV written to: " + out);
    }

    private String format(String scenario, String stack, Result r) {
        return String.format(Locale.ROOT, "%s,%s,%.2f,%.2f,%.2f,%d,%.2f,%.2f,%.2f",
                scenario, stack, r.avgUs, r.p95Us, r.throughputOpsPerSec, r.effectiveOpsPerRound,
                r.avgHeapDeltaMb, r.avgCpuMs, r.avgGcMs);
    }

    // WORM path constrained to ActiveRecord + Persistable + Deletable APIs only.
    private Result runWormLike(String scenario, int ops, int rounds) {
        List<Double> perOpUs = new ArrayList<>();
        List<Double> tps = new ArrayList<>();
        List<Double> heapDeltas = new ArrayList<>();
        List<Double> cpuTimes = new ArrayList<>();
        List<Double> gcTimes = new ArrayList<>();
        for (int i = 0; i < rounds; i++) {
            System.gc();
            long heapBefore = usedHeapMb();
            long cpuBefore  = cpuSnapshot();
            long gcBefore   = gcTimeMs();
            long durationMs = switch (scenario) {
                case "selectById"          -> worm_selectById(ops);
                case "selectCountByStatus" -> worm_selectCountByStatus(ops);
                case "selectPageByAuthor"  -> worm_selectPageByAuthor(ops);
                case "selectJoinBookAuthor"-> worm_selectJoin(ops);
                case "insertSingle"        -> worm_insertSingle(ops);
                case "insertBatch"         -> worm_insertBatch(ops);
                case "updateSingle"        -> worm_updateSingle(ops);
                case "updateBatch"         -> worm_updateBatch(ops);
                case "deleteSingle"        -> worm_deleteSingle(ops);
                case "deleteBatch"         -> worm_deleteBatch(ops);
                default -> 1;
            };
            heapDeltas.add((double) Math.max(0, usedHeapMb() - heapBefore));
            cpuTimes.add((cpuSnapshot() - cpuBefore) / 1_000_000.0);
            gcTimes.add((double) (gcTimeMs() - gcBefore));
            double perOp = durationMs * 1000.0 / Math.max(1, ops);
            perOpUs.add(perOp);
            tps.add(ops / (durationMs / 1000.0 + 1e-9));
        }
        return summarize(perOpUs, tps, ops, heapDeltas, cpuTimes, gcTimes);
    }

    // JPA path constrained to repository-only calls.
    private Result runJpaLike(String scenario, int ops, int rounds) {
        List<Double> perOpUs = new ArrayList<>();
        List<Double> tps = new ArrayList<>();
        List<Double> heapDeltas = new ArrayList<>();
        List<Double> cpuTimes = new ArrayList<>();
        List<Double> gcTimes = new ArrayList<>();
        for (int i = 0; i < rounds; i++) {
            System.gc();
            long heapBefore = usedHeapMb();
            long cpuBefore  = cpuSnapshot();
            long gcBefore   = gcTimeMs();
            Long durationMsObj = transactionTemplate.execute(status -> switch (scenario) {
                case "selectById"          -> jpa_selectById(ops);
                case "selectCountByStatus" -> jpa_selectCountByStatus(ops);
                case "selectPageByAuthor"  -> jpa_selectPageByAuthor(ops);
                case "selectJoinBookAuthor"-> jpa_selectJoin(ops);
                case "insertSingle"        -> jpa_insertSingle(ops);
                case "insertBatch"         -> jpa_insertBatch(ops);
                case "updateSingle"        -> jpa_updateSingle(ops);
                case "updateBatch"         -> jpa_updateBatch(ops);
                case "deleteSingle"        -> jpa_deleteSingle(ops);
                case "deleteBatch"         -> jpa_deleteBatch(ops);
                default -> 1L;
            });
            heapDeltas.add((double) Math.max(0, usedHeapMb() - heapBefore));
            cpuTimes.add((cpuSnapshot() - cpuBefore) / 1_000_000.0);
            gcTimes.add((double) (gcTimeMs() - gcBefore));
            long durationMs = durationMsObj == null ? 1L : durationMsObj;
            double perOp = durationMs * 1000.0 / Math.max(1, ops);
            perOpUs.add(perOp);
            tps.add(ops / (durationMs / 1000.0 + 1e-9));
        }
        return summarize(perOpUs, tps, ops, heapDeltas, cpuTimes, gcTimes);
    }

    private Result summarize(List<Double> perOpUs, List<Double> tps, int ops,
                              List<Double> heapDeltas, List<Double> cpuTimes, List<Double> gcTimes) {
        double avg = perOpUs.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        List<Double> sorted = perOpUs.stream().sorted().toList();
        int idx = Math.max(0, Math.min(sorted.size() - 1, (int) Math.ceil(0.95 * sorted.size()) - 1));
        double p95 = sorted.get(idx);
        double avgT       = avg(tps);
        double avgHeap    = avg(heapDeltas);
        double avgCpu     = avg(cpuTimes);
        double avgGc      = avg(gcTimes);
        return new Result(avg, p95, avgT, ops, avgHeap, avgCpu, avgGc);
    }

    private static double avg(List<Double> list) {
        return list.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private String newRunTag() {
        return UUID.randomUUID().toString();
    }

    private record WormSeed(List<Author> authors, List<Book> books) {}

    private WormSeed seedWormData(int count) {
        String runTag = newRunTag();
        List<Author> authors = new ArrayList<>();
        List<Book> books = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Author author = Author.builder()
                    .id(UUID.randomUUID())
                    .name("A" + i)
                    .email("a" + i + "+" + runTag + "@x")
                    .build();
            Book book = Book.builder()
                    .id(UUID.randomUUID())
                    .title("B" + i)
                    .isbn("isbn" + i + "-" + runTag)
                    .status("AVAILABLE")
                    .authorId(author.getId())
                    .active(true)
                    .build();
            authors.add(author);
            books.add(book);
        }
        Persistable.saveAll(authors);
        Persistable.saveAll(books);
        return new WormSeed(authors, books);
    }

    private void cleanupWorm() {
        jdbcTemplate.update("DELETE FROM books");
        jdbcTemplate.update("DELETE FROM authors");
    }

    private long worm_insertSingle(int count) {
        cleanupWorm();
        String runTag = newRunTag();
        long start = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            Author author = Author.builder()
                    .id(UUID.randomUUID())
                    .name("A" + i)
                    .email("a" + i + "+" + runTag + "@x")
                    .build();
            author.save();
            Book book = Book.builder()
                    .id(UUID.randomUUID())
                    .title("B" + i)
                    .isbn("isbn" + i + "-" + runTag)
                    .status("AVAILABLE")
                    .authorId(author.getId())
                    .active(true)
                    .build();
            book.save();
        }
        return System.currentTimeMillis() - start;
    }

    private long worm_insertBatch(int count) {
        cleanupWorm();
        long start = System.currentTimeMillis();
        seedWormData(count);
        return System.currentTimeMillis() - start;
    }

    private long worm_updateSingle(int count) {
        cleanupWorm();
        WormSeed seed = seedWormData(count);
        long start = System.currentTimeMillis();
        for (Book b : seed.books()) {
            b.setTitle(b.getTitle() + " - U");
            Persistable.update(b);
        }
        return System.currentTimeMillis() - start;
    }

    private long worm_updateBatch(int count) {
        cleanupWorm();
        WormSeed seed = seedWormData(count);
        for (Book b : seed.books()) {
            b.setTitle(b.getTitle() + " - U");
        }
        long start = System.currentTimeMillis();
        Persistable.updateAll(seed.books());
        return System.currentTimeMillis() - start;
    }

    private long worm_deleteSingle(int count) {
        cleanupWorm();
        WormSeed seed = seedWormData(count);
        long start = System.currentTimeMillis();
        for (Book b : seed.books()) {
            b.delete();
        }
        return System.currentTimeMillis() - start;
    }

    private long worm_deleteBatch(int count) {
        cleanupWorm();
        WormSeed seed = seedWormData(count);
        long start = System.currentTimeMillis();
        Deletable.deleteAll(seed.books());
        return System.currentTimeMillis() - start;
    }

    private long worm_selectById(int count) {
        cleanupWorm();
        WormSeed seed = seedWormData(count);
        long start = System.currentTimeMillis();
        for (Book b : seed.books()) {
            Book.find.byId(b.getId());
        }
        return System.currentTimeMillis() - start;
    }

    private long worm_selectCountByStatus(int count) {
        cleanupWorm();
        seedWormData(count);
        long start = System.currentTimeMillis();
        Book.find.count(FilterBuilder.create().eq("status", "AVAILABLE"));
        return System.currentTimeMillis() - start;
    }

    private long worm_selectPageByAuthor(int count) {
        cleanupWorm();
        WormSeed seed = seedWormData(count);
        if (seed.books().isEmpty()) {
            return 0L;
        }
        UUID authorId = seed.books().getFirst().getAuthorId();
        long start = System.currentTimeMillis();
        var page = Book.find.all(
                FilterBuilder.create().eq("author_id", authorId),
                Pageable.of(PAGE_NUMBER, PAGE_SIZE)
        );
        if (page == null) {
            throw new IllegalStateException("Unreachable");
        }
        return System.currentTimeMillis() - start;
    }

    private long worm_selectJoin(int count) {
        cleanupWorm();
        WormSeed seed = seedWormData(count);
        long start = System.currentTimeMillis();
        for (Book b : seed.books()) {
            Author.find.byId(b.getAuthorId());
        }
        return System.currentTimeMillis() - start;
    }

    private void cleanupJpa() {
        jdbcTemplate.update("DELETE FROM books");
        jdbcTemplate.update("DELETE FROM authors");
    }

    private long jpa_insertSingle(int count) {
        cleanupJpa();
        String runTag = newRunTag();
        long start = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            JpaAuthor a = JpaAuthor.builder().id(UUID.randomUUID()).name("A" + i).email("a" + i + "+" + runTag + "@x").build();
            JpaAuthor savedAuthor = authorRepository.save(a);
            JpaBook b = JpaBook.builder().id(UUID.randomUUID()).title("B" + i).isbn("isbn" + i + "-" + runTag).status("AVAILABLE").authorId(savedAuthor.getId()).active(true).build();
            bookRepository.save(b);
        }
        return System.currentTimeMillis() - start;
    }

    private long jpa_insertBatch(int count) {
        cleanupJpa();
        String runTag = newRunTag();
        long start = System.currentTimeMillis();
        List<JpaAuthor> authors = new ArrayList<>();
        List<JpaBook> books = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            JpaAuthor a = JpaAuthor.builder().id(UUID.randomUUID()).name("A" + i).email("a" + i + "+" + runTag + "@x").build();
            authors.add(a);
            books.add(JpaBook.builder().id(UUID.randomUUID()).title("B" + i).isbn("isbn" + i + "-" + runTag).status("AVAILABLE").authorId(a.getId()).active(true).build());
        }
        authorRepository.saveAll(authors);
        bookRepository.saveAll(books);
        return System.currentTimeMillis() - start;
    }

    private long jpa_updateSingle(int count) {
        jpa_insertBatch(count);
        List<JpaBook> books = bookRepository.findAll();
        long start = System.currentTimeMillis();
        for (JpaBook b : books) {
            b.setTitle(b.getTitle() + " - U");
            bookRepository.save(b);
        }
        return System.currentTimeMillis() - start;
    }

    private long jpa_updateBatch(int count) {
        jpa_insertBatch(count);
        List<JpaBook> books = bookRepository.findAll();
        for (JpaBook b : books) {
            b.setTitle(b.getTitle() + " - U");
        }
        long start = System.currentTimeMillis();
        bookRepository.saveAll(books);
        return System.currentTimeMillis() - start;
    }

    private long jpa_deleteSingle(int count) {
        jpa_insertBatch(count);
        List<JpaBook> books = bookRepository.findAll();
        long start = System.currentTimeMillis();
        for (JpaBook b : books) {
            bookRepository.delete(b);
        }
        return System.currentTimeMillis() - start;
    }

    private long jpa_deleteBatch(int count) {
        jpa_insertBatch(count);
        long start = System.currentTimeMillis();
        bookRepository.deleteAll();
        authorRepository.deleteAll();
        return System.currentTimeMillis() - start;
    }

    private long jpa_selectById(int count) {
        jpa_insertBatch(count);
        List<UUID> ids = bookRepository.findAll().stream().map(JpaBook::getId).collect(Collectors.toList());
        long start = System.currentTimeMillis();
        for (UUID id : ids) {
            bookRepository.findById(id);
        }
        return System.currentTimeMillis() - start;
    }

    private long jpa_selectCountByStatus(int count) {
        jpa_insertBatch(count);
        long start = System.currentTimeMillis();
        bookRepository.findByStatus("AVAILABLE");
        return System.currentTimeMillis() - start;
    }

    private long jpa_selectPageByAuthor(int count) {
        jpa_insertBatch(count);
        List<JpaBook> all = bookRepository.findAll();
        if (all.isEmpty()) {
            return 0L;
        }
        UUID aid = all.getFirst().getAuthorId();
        long start = System.currentTimeMillis();
        List<JpaBook> page = bookRepository.findByAuthorIdPaged(aid, PAGE_NUMBER, Math.min(PAGE_SIZE, count));
        if (page == null) {
            throw new IllegalStateException("Unreachable");
        }
        return System.currentTimeMillis() - start;
    }

    private long jpa_selectJoin(int count) {
        jpa_insertBatch(count);
        List<JpaBook> books = bookRepository.findAll();
        long start = System.currentTimeMillis();
        for (JpaBook book : books) {
            Optional<JpaAuthor> ignored = authorRepository.findById(book.getAuthorId());
            if (ignored.isEmpty()) {
                throw new IllegalStateException("Author not found for existing book");
            }
        }
        return System.currentTimeMillis() - start;
    }
}

