package br.com.worm.demo.service;

import br.com.liviacare.worm.orm.OrmOperations;
import br.com.liviacare.worm.query.FilterBuilder;
import br.com.worm.demo.Author;
import br.com.worm.demo.Book;
import br.com.worm.demo.dto.BookProjection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * High-performance optimization service showcasing best WORM patterns (Section 23).
 * Focuses on:
 *  - Projections (Finder-style records)
 *  - Column queries (single-column selects)
 *  - Bulk batch operations (PostgreSQL COPY/unnest)
 *  - Read-modify-write updates
 */
@Service
public class OptimizationService {

    private final OrmOperations orm;

    public OptimizationService(OrmOperations orm) {
        this.orm = orm;
    }

    // ── Projection Queries (Section 10, 27.2) ──────────────────────────────
    
    /**
     * Fetch only needed columns via projection record using Finder style.
     */
    public List<BookProjection> findAllBooksProjected() {
        return BookProjection.find.all(FilterBuilder.create().orderBy("created_at", false));
    }

    public List<BookProjection> findActiveBooks() {
        return BookProjection.find.all(
                FilterBuilder.create()
                        .eq("active", true)
                        .orderBy("created_at", false));
    }

    // ── Column Queries (Section 10) ─────────────────────────────────────────

    /** Single-column query — only fetch ISBNs, no row mapping overhead. */
    public List<String> findAllIsbnsCached() {
        return orm.findColumn(Book.class, "isbn", String.class, FilterBuilder.create());
    }

    /** Single-column query — only fetch author IDs. */
    public List<java.util.UUID> findAllAuthorIds() {
        return orm.findColumn(Book.class, "author_id", java.util.UUID.class, FilterBuilder.create());
    }

    /** Single-column query — only fetch author emails. */
    public List<String> findAllAuthorEmails() {
        return orm.findColumn(Author.class, "email", String.class, FilterBuilder.create());
    }

    // ── Bulk Batch Operations (Section 16, 27.4) ───────────────────────────

    /**
     * Bulk-optimised batch insert.
     * Routes to PostgreSQL COPY above worm.bulk-copy-threshold (20 rows).
     * Significantly faster than single inserts or standard JDBC batch.
     */
    @Transactional
    public void bulkInsertBooks(List<Book> books) {
        orm.saveAllBatch(books);
    }

    /**
     * Bulk-optimised batch update.
     * Routes to PostgreSQL unnest array UPDATE above worm.bulk-unnest-threshold (10 rows).
     * Much faster than iterative updates.
     */
    @Transactional
    public void bulkUpdateBooks(List<Book> books) {
        orm.updateAllBatch(books);
    }

    /**
     * Bulk-optimised batch delete.
     * Routes to PostgreSQL unnest array DELETE above worm.bulk-unnest-threshold (10 rows).
     */
    @Transactional
    public void bulkDeleteBooks(List<Book> books) {
        orm.deleteAllBatch(books);
    }

    // ── Read-modify-write updates ──────────────────────────────────────────

    /**
     * Load -> modify -> update pattern (Section 23.3).
     * Keeps writes explicit and predictable for hot paths.
     */
    @Transactional
    public void optimizedUpdate(java.util.UUID bookId, String newTitle) {
        Book book = orm.findById(Book.class, bookId).orElseThrow();
        book.setTitle(newTitle);
        orm.update(book);
    }

    // ── Query Plan Cache (Section 27.1) ─────────────────────────────────────

    /**
     * First call: FilterBuilder SQL is compiled and cached.
     * Subsequent calls: cached SQL reused directly (no StringBuilder overhead).
     * Same query shape = huge speedup.
     */
    public List<Book> findActiveByStatus(String status) {
        // Query shape: "... WHERE active=? AND status=? ORDER BY ..."
        // First call: SQL compiled, cached. Second call: cache hit.
        return orm.findAll(Book.class,
                FilterBuilder.create()
                        .eq("active", true)
                        .eq("status", status)
                        .orderBy("created_at", false));
    }

    public List<Author> findByNamePattern(String pattern) {
        // Different pattern arg but same SQL shape = cache hit
        return orm.findAll(Author.class,
                FilterBuilder.create()
                        .like("name", pattern)
                        .orderBy("name"));
    }
}
