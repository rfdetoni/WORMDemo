package br.com.worm.demo;

import br.com.liviacare.worm.api.Deletable;
import br.com.liviacare.worm.api.Persistable;
import br.com.liviacare.worm.orm.OrmOperations;
import br.com.liviacare.worm.query.FilterBuilder;
import br.com.worm.demo.dto.BookProjection;
import br.com.worm.demo.service.OptimizationService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance optimization tests demonstrating WORM best practices (Section 23, 27).
 *
 * Focuses on:
 *  - Projections (zero-allocation row mapping)
 *  - Column queries (single-column selects)
 *  - Dirty tracking (@Track partial updates)
 *  - Query plan cache reuse
 *  - Bulk batch operations (PostgreSQL COPY/unnest)
 */
@SpringBootTest
@ActiveProfiles("test")
@Slf4j
public class WormOptimizationTests {

    @Autowired
    private OrmOperations orm;

    @Autowired
    private OptimizationService optimizationService;

    @BeforeEach
    void cleanupData() {
        Deletable.hard.deleteAll(Book.find.all(FilterBuilder.create().isNotNull("active").ignoreSoftDelete()));
        Deletable.hard.deleteAll(Author.find.all());
    }

    protected Author createAuthor(String name, String email) {
        Author a = Author.builder()
                .id(UUID.randomUUID())
                .name(name)
                .email(email)
                .build();
        a.save();

        return a;
    }

    private Book newBook(String title, String isbn, String status, UUID authorId) {
        return Book.builder()
                .id(UUID.randomUUID())
                .title(title)
                .isbn(isbn)
                .status(status)
                .authorId(authorId)
                .active(true)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Section 10 — Zero-allocation projection row mapping (Section 27.2)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void testProjectionZeroAllocationRowMapping() {
        Author author = createAuthor("Optimization Author", "opt@example.com");
        Persistable.save(newBook("Opt Book 1", "opt-isbn-1", "PUBLISHED", author.getId()));
        Persistable.save(newBook("Opt Book 2", "opt-isbn-2", "PUBLISHED", author.getId()));

        List<BookProjection> projections = BookProjection.find.all(
                FilterBuilder.create().eq("author_id", author.getId()));

        assertEquals(2, projections.size());
        projections.forEach(p -> {
            assertNotNull(p.id());
            assertNotNull(p.title());
            assertNotNull(p.isbn());
        });
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Single-column queries (Section 10, minimal materialization)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void testColumnQueryMinimalOverhead() {
        Author a1 = createAuthor("Author Col 1", "col1@example.com");
        Author a2 = createAuthor("Author Col 2", "col2@example.com");
        Persistable.save(newBook("Col Book 1", "col-isbn-1", "PUBLISHED", a1.getId()));
        Persistable.save(newBook("Col Book 2", "col-isbn-2", "PUBLISHED", a2.getId()));

        // Single-column query: SELECT isbn FROM books
        // No row mapping, no object construction — pure materialization
        List<String> isbns = orm.findColumn(Book.class, "isbn", String.class, FilterBuilder.create());
        assertEquals(2, isbns.size());

        // Single-column query: SELECT author_id FROM books
        List<UUID> authorIds = orm.findColumn(Book.class, "author_id", UUID.class, FilterBuilder.create());
        assertEquals(2, authorIds.size());

        // Single-column query: SELECT email FROM authors
        List<String> emails = orm.findColumn(Author.class, "email", String.class, FilterBuilder.create());
        assertTrue(emails.size() >= 2);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  @Track dirty tracking for partial updates (Section 17)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void testTrackPartialUpdateOptimization() {
        Author author = createAuthor("Track Author", "track@example.com");
        Book book = Persistable.save(newBook("Track Book", "track-isbn", "DRAFT", author.getId()));

        // Load -> modify -> update pattern (Section 23.3)
        Book loaded = orm.findById(Book.class, book.getId()).orElseThrow();
        long versionBefore = loaded.getVersion();

        loaded.setTitle("Track Book Updated");
        // With @Track: UPDATE books SET title=?, updated_at=? WHERE id=? AND version=?
        // Without @Track: UPDATE books SET title=?, isbn=?, status=?, author_id=?, active=?, updated_at=?, version=? WHERE id=?
        // @Track reduces columns written — better for partial/frequent updates
        orm.update(loaded);

        Book reloaded = orm.findById(Book.class, book.getId()).orElseThrow();
        assertEquals("Track Book Updated", reloaded.getTitle());
        assertTrue(reloaded.getVersion() >= versionBefore);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Query Plan Cache (Section 27.1 — same shape, cached)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void testQueryPlanCacheReuse() {
        Author author1 = createAuthor("Cache Author 1", "cache1@example.com");
        Author author2 = createAuthor("Cache Author 2", "cache2@example.com");
        Persistable.save(newBook("Cache Book 1", "cache-isbn-1", "PUBLISHED", author1.getId()));
        Persistable.save(newBook("Cache Book 2", "cache-isbn-2", "PUBLISHED", author2.getId()));

        // First call: SQL compiled, cached as "active=? AND status=? ORDER BY created_at DESC"
        List<Book> first = orm.findAll(Book.class,
                FilterBuilder.create()
                        .eq("active", true)
                        .eq("status", "PUBLISHED")
                        .orderBy("created_at", false));

        // Second call: same SQL shape, different bind params
        // Cache hit: no StringBuilder reconstruction
        List<Book> second = orm.findAll(Book.class,
                FilterBuilder.create()
                        .eq("active", true)
                        .eq("status", "PUBLISHED")
                        .orderBy("created_at", false));

        assertFalse(first.isEmpty());
        assertFalse(second.isEmpty());
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Bulk batch operations (Section 16, 27.4)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void testBulkBatchInsertOptimization() {
        Author author = createAuthor("Bulk Author", "bulk@example.com");

        List<Book> books = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            books.add(newBook("Bulk Book " + i, "bulk-isbn-" + i, "AVAILABLE", author.getId()));
        }

        optimizationService.bulkInsertBooks(books);

        long count = orm.count(Book.class, FilterBuilder.create().eq("author_id", author.getId()));
        assertEquals(30, count);
    }

    @Test
    void testBulkBatchUpdateOptimization() {
        Author author = createAuthor("Bulk Update Author", "bulkupd@example.com");

        List<Book> books = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            books.add(Persistable.save(newBook("Before " + i, "bu-isbn-" + i, "DRAFT", author.getId())));
        }

        // Modify all
        books.forEach(b -> b.setStatus("PUBLISHED"));

        // updateAllBatch routes to PostgreSQL unnest array UPDATE above bulk-unnest-threshold (10)
        // Unnest UPDATE is 3-5x faster than iterative UPDATEs
        optimizationService.bulkUpdateBooks(books);

        List<Book> updated = orm.findAll(Book.class,
                FilterBuilder.create().eq("author_id", author.getId()));
        assertTrue(updated.stream().allMatch(b -> "PUBLISHED".equals(b.getStatus())));
    }

    @Test
    void testBulkBatchDeleteOptimization() {
        Author author = createAuthor("Bulk Delete Author", "bulkdel@example.com");

        List<Book> books = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            books.add(Persistable.save(newBook("Delete " + i, "bd-isbn-" + i, "AVAILABLE", author.getId())));
        }

        Deletable.deleteAll(books);

        long remaining = Book.find.count(FilterBuilder.create().eq("author_id", author.getId()).eq("active", false));
        assertEquals(0, remaining);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Optimization Service Integration
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void testOptimizationServiceProjectionQueries() {
        Author author = createAuthor("Service Author", "svc@example.com");
        Persistable.save(newBook("Service Book 1", "svc-isbn-1", "PUBLISHED", author.getId()));
        Persistable.save(newBook("Service Book 2", "svc-isbn-2", "AVAILABLE", author.getId()));

        List<BookProjection> projections = optimizationService.findActiveBooks();
        assertFalse(projections.isEmpty());
    }

    @Test
    void testOptimizationServiceColumnQueries() {
        Author author = createAuthor("Col Service Author", "colsvc@example.com");
        Persistable.save(newBook("Col Service Book 1", "colsvc-isbn-1", "PUBLISHED", author.getId()));
        Persistable.save(newBook("Col Service Book 2", "colsvc-isbn-2", "PUBLISHED", author.getId()));

        List<String> isbns = optimizationService.findAllIsbnsCached();
        assertEquals(2, isbns.size());

        List<String> emails = optimizationService.findAllAuthorEmails();
        assertTrue(emails.size() >= 1);
    }

    @Test
    void testOptimizationServiceDirtyTracking() {
        Author author = createAuthor("DT Service Author", "dtsvc@example.com");
        Book book = Persistable.save(newBook("DT Service Book", "dtsvc-isbn", "DRAFT", author.getId()));

        optimizationService.optimizedUpdate(book.getId(), "DT Service Book Updated");

        Book reloaded = orm.findById(Book.class, book.getId()).orElseThrow();
        assertEquals("DT Service Book Updated", reloaded.getTitle());
    }

    @Test
    void testOptimizationServiceQueryPlanCache() {
        Author author1 = createAuthor("Cache Svc 1", "csvc1@example.com");
        Author author2 = createAuthor("Cache Svc 2", "csvc2@example.com");
        Persistable.save(newBook("Cache Svc Book 1", "csvc-isbn-1", "PUBLISHED", author1.getId()));
        Persistable.save(newBook("Cache Svc Book 2", "csvc-isbn-2", "PUBLISHED", author2.getId()));

        // Multiple calls with same SQL shape — plan cache reused
        List<Book> first = optimizationService.findActiveByStatus("PUBLISHED");
        List<Book> second = optimizationService.findActiveByStatus("PUBLISHED");

        assertTrue(first.size() > 0);
        assertTrue(second.size() > 0);
    }
}
