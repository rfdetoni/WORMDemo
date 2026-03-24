package br.com.worm.demo.service;

import br.com.liviacare.worm.orm.OrmOperations;
import br.com.liviacare.worm.query.FilterBuilder;
import br.com.liviacare.worm.query.Page;
import br.com.liviacare.worm.query.Pageable;
import br.com.worm.demo.Author;
import br.com.worm.demo.dto.AuthorSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Author service using OrmOperations (Section 7 — Pattern B).
 *
 * Demonstrates:
 *  - findById / findOne / findAll
 *  - findPage (Page with total count)
 *  - projection to AuthorSummary record
 *  - aggregates: count, exists, avg, findColumn
 *  - batch writes: saveAllBatch, updateAllBatch, deleteAllBatch
 *  - filter predicates: like, in, openParen/or/closeParen, withCte
 */
@Service
public class AuthorService {

    private final OrmOperations orm;

    public AuthorService(OrmOperations orm) {
        this.orm = orm;
    }

    // ── Reads ──────────────────────────────────────────────────────────────

    public Optional<Author> findById(UUID id) {
        return orm.findById(Author.class, id);
    }

    /** Lightweight projection — only id, name, email fetched. */
    public Optional<AuthorSummary> findSummaryById(UUID id) {
        return AuthorSummary.find.byId(id);
    }

    public List<Author> findAll() {
        return orm.findAll(Author.class, FilterBuilder.create());
    }

    /** Find by partial name (LIKE). */
    public List<Author> findByName(String nameLike) {
        return orm.findAll(Author.class,
                FilterBuilder.create().like("name", "%" + nameLike + "%"));
    }

    /** Find authors whose email matches any of the supplied values (IN predicate). */
    public List<Author> findByEmails(List<String> emails) {
        return orm.findAll(Author.class,
                FilterBuilder.create().in("email", emails).orderBy("name"));
    }

    /** OR grouping: match name OR email contains the given keyword. */
    public List<Author> search(String keyword) {
        String pattern = "%" + keyword + "%";
        return orm.findAll(Author.class,
                FilterBuilder.create()
                        .openParen()
                            .like("name",  pattern)
                            .or()
                            .like("email", pattern)
                        .closeParen()
                        .orderBy("name"));
    }

    /** Projection list — only id, name, email columns for all authors. */
    public List<AuthorSummary> findAllSummaries() {
        return AuthorSummary.find.all(FilterBuilder.create().orderBy("name"));
    }

    /** Page API: returns total element count + total pages alongside content. */
    public Page<Author> findPage(int page, int size) {
        return orm.findPage(Author.class,
                FilterBuilder.create().orderBy("created_at", false),
                Pageable.of(page, size));
    }

    // ── Aggregates ─────────────────────────────────────────────────────────

    public long count() {
        return orm.count(Author.class);
    }

    public boolean existsById(UUID id) {
        return orm.existsById(Author.class, id);
    }

    /** Return only the email column as a String list. */
    public List<String> findAllEmails() {
        return orm.findColumn(Author.class, "email", String.class, FilterBuilder.create().orderBy("email"));
    }

    // ── CTE example ────────────────────────────────────────────────────────

    /** Use a CTE to fetch recently created authors (last 30 days). */
    public List<Author> findRecent() {
        FilterBuilder filter = FilterBuilder.create()
                .withCte("recent_authors",
                         "select * from authors where created_at >= now() - interval '30 days'")
                .orderBy("created_at", false);
        return orm.findAllWithCte(Author.class, filter);
    }

    // ── Writes ─────────────────────────────────────────────────────────────

    @Transactional
    public Author save(Author author) {
        orm.save(author);
        return author;
    }

    @Transactional
    public void update(Author author) {
        orm.update(author);
    }

    /** Standard batch (JDBC batch under the hood). */
    @Transactional
    public void saveAll(List<Author> authors) {
        orm.saveAll(authors);
    }

    /** Bulk-optimised batch — routes to PostgreSQL COPY above worm.bulk-copy-threshold. */
    @Transactional
    public void saveAllBatch(List<Author> authors) {
        orm.saveAllBatch(authors);
    }

    /** Bulk-optimised update — routes to unnest array UPDATE above worm.bulk-unnest-threshold. */
    @Transactional
    public void updateAllBatch(List<Author> authors) {
        orm.updateAllBatch(authors);
    }

    /** Bulk-optimised delete. */
    @Transactional
    public void deleteAllBatch(List<Author> authors) {
        orm.deleteAllBatch(authors);
    }

    @Transactional
    public void deleteById(UUID id) {
        orm.deleteById(Author.class, id);
    }
}
