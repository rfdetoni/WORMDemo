package br.com.worm.demo.service;

import br.com.liviacare.worm.orm.OrmOperations;
import br.com.liviacare.worm.query.FilterBuilder;
import br.com.liviacare.worm.query.Page;
import br.com.liviacare.worm.query.Pageable;
import br.com.liviacare.worm.query.Slice;
import br.com.worm.demo.Book;
import br.com.worm.demo.dto.BookDto;
import br.com.worm.demo.mapper.BookMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Book service — mixes ActiveRecord/Finder style (for BookDto projections)
 * with OrmOperations (for Book entity writes and advanced queries).
 *
 * Demonstrates:
 *  - BookDto.find.* (Finder/projection via record @DbTable)
 *  - orm.findPage    (Page API with total count)
 *  - orm.findAll     (filtered, active-only, OR groups)
 *  - ignoreSoftDelete (bypass automatic soft-delete filter)
 *  - aggregate count
 *  - saveAllBatch / updateAllBatch / deleteAllBatch (bulk paths)
 */
@Service
public class BookService {

    private final OrmOperations orm;

    public BookService(OrmOperations orm) {
        this.orm = orm;
    }

    // ── BookDto (projection) reads via Finder style ─────────────────────────

    public BookDto findById(UUID id) {
        return BookDto.find.byId(id).orElse(null);
    }

    public List<BookDto> findAll() {
        return BookDto.find.all();
    }

    public List<BookDto> findAllByFilter(FilterBuilder filter) {
        return BookDto.find.all(filter);
    }

    public Slice<BookDto> findAllPaged(FilterBuilder filter, Pageable pageable) {
        return BookDto.find.all(filter, pageable);
    }

    // ── Book entity reads via OrmOperations ────────────────────────────────

    public Optional<Book> findBookById(UUID id) {
        return orm.findById(Book.class, id);
    }

    /**
     * Page API: returns content + totalElements + totalPages.
     * Use when the caller needs to display pagination controls.
     */
    public Page<Book> findPage(FilterBuilder filter, Pageable pageable) {
        return orm.findPage(Book.class, filter, pageable);
    }

    /** Only active books, newest first. */
    public List<Book> findActiveBooks() {
        return Book.find.all(
                FilterBuilder.create()
                        .eq("active", true)
                        .orderBy("created_at", false));
    }

    /**
     * OR group: books that match the given status OR are active.
     * Demonstrates openParen / or() / closeParen predicates.
     */
    public List<Book> findByStatusOrActive(String status) {
        return Book.find.all(
                FilterBuilder.create()
                        .openParen()
                            .eq("status", status)
                            .or()
                            .eq("active", true)
                        .closeParen()
                        .orderBy("created_at", false));
    }

    /**
     * Bypass soft-delete filter to include logically deleted rows.
     * Useful for admin views or audit trails.
     */
    public List<Book> findIncludingDeleted() {
        return Book.find.all(FilterBuilder.create().ignoreSoftDelete());
    }

    public long countByStatus(String status) {
        return Book.find.count(FilterBuilder.create().eq("status", status));
    }

    public boolean existsById(UUID id) {
        return orm.existsById(Book.class, id);
    }

    // ── Writes ─────────────────────────────────────────────────────────────

    @Transactional
    public Book save(Book book) {
        book.save();
        return book;
    }

    @Transactional
    public void create(String title, String isbn, String status, UUID authorId) {
        Book book = BookMapper.fromCreate(title, isbn, status == null ? "DRAFT" : status, authorId);
        book.save();
    }

    /** Bulk-optimised insert — routes to PostgreSQL COPY above worm.bulk-copy-threshold. */
    @Transactional
    public void saveAllBatch(List<Book> books) {
        orm.saveAllBatch(books);
    }

    /** Bulk-optimised update — routes to unnest array UPDATE above worm.bulk-unnest-threshold. */
    @Transactional
    public void updateAllBatch(List<Book> books) {
        orm.updateAllBatch(books);
    }

    /** Bulk-optimised delete (physical). */
    @Transactional
    public void deleteAllBatch(List<Book> books) {
        orm.deleteAllBatch(books);
    }

    @Transactional
    public void deleteById(UUID id) {
        orm.deleteById(Book.class, id);
    }
}
