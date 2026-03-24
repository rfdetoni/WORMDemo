package br.com.worm.demo;

import br.com.liviacare.worm.api.Deletable;
import br.com.liviacare.worm.orm.OrmOperations;
import br.com.liviacare.worm.query.FilterBuilder;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Benchmark service with optimized WORM patterns (Section 27).
 * Uses bulk paths (saveAllBatch, updateAllBatch, deleteAllBatch) for fair JPA comparison.
 */
@Service
@RequiredArgsConstructor
public class BenchmarkService {

    private final JpaAuthorRepository jpaAuthorRepository;
    private final JpaBookRepository jpaBookRepository;
    private final OrmOperations orm;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void cleanDatabase() {
        Deletable.deleteAll(Book.find.all(FilterBuilder.create().ignoreSoftDelete()));
        Deletable.deleteAll(Author.find.all());
    }

    @Transactional
    public void cleanDatabaseJpa() {
        jpaBookRepository.deleteAll();
        jpaAuthorRepository.deleteAll();
        entityManager.flush();
        entityManager.clear();
    }
    
    // ── WORM: Optimized with bulk paths ────────────────────────────────────

    /** WORM unitary insert — one-by-one persistence. */
    public long wormUnitaryInsert(int count) {
        cleanDatabase();
        long start = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            Author author = Author.builder()
                    .id(UUID.randomUUID())
                    .name("Author " + i)
                    .email("author" + i + "@test.com")
                    .build();
            author.save();

            Book book = Book.builder()
                    .id(UUID.randomUUID())
                    .title("Book " + i)
                    .isbn("ISBN-" + i)
                    .status("AVAILABLE")
                    .authorId(author.getId())
                    .active(true)
                    .build();
            book.save();
        }
        return System.currentTimeMillis() - start;
    }
    
    /** WORM bulk-optimised batch insert (routes to PostgreSQL COPY above bulk-copy-threshold). */
    public long wormBatchInsert(int count) {
        cleanDatabase();
        List<Author> authors = new ArrayList<>();
        List<Book> books = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Author author = Author.builder()
                    .id(UUID.randomUUID())
                    .name("Author " + i)
                    .email("author" + i + "@test.com")
                    .build();
            authors.add(author);

            Book book = Book.builder()
                    .id(UUID.randomUUID())
                    .title("Book " + i)
                    .isbn("ISBN-" + i)
                    .status("AVAILABLE")
                    .authorId(author.getId())
                    .active(true)
                    .build();
            books.add(book);
        }

        long start = System.currentTimeMillis();
        // Routes to PostgreSQL COPY above worm.bulk-copy-threshold (20 rows)
        orm.saveAllBatch(authors);
        orm.saveAllBatch(books);
        return System.currentTimeMillis() - start;
    }

    /** WORM unitary update — one-by-one. */
    public long wormUnitaryUpdate(int count) {
        wormBatchInsert(count);
        List<Book> books = Book.find.all();
        long start = System.currentTimeMillis();
        for (Book book : books) {
            book.setTitle(book.getTitle() + " - Updated");
            orm.update(book);
        }
        return System.currentTimeMillis() - start;
    }

    /** WORM bulk-optimised batch update (routes to PostgreSQL unnest array above threshold). */
    public long wormBatchUpdate(int count) {
        wormBatchInsert(count);
        List<Book> books = Book.find.all();
        for (Book book : books) {
            book.setTitle(book.getTitle() + " - Updated");
        }
        long start = System.currentTimeMillis();
        // Routes to PostgreSQL unnest array UPDATE above worm.bulk-unnest-threshold (10 rows)
        orm.updateAllBatch(books);
        return System.currentTimeMillis() - start;
    }

    /** WORM unitary delete — one-by-one. */
    public long wormUnitaryDelete(int count) {
        wormBatchInsert(count);
        List<Book> books = Book.find.all();
        long start = System.currentTimeMillis();
        for (Book book : books) {
            orm.delete(book);  // soft delete
        }
        return System.currentTimeMillis() - start;
    }

    /** WORM bulk-optimised batch delete. */
    public long wormBatchDelete(int count) {
        wormBatchInsert(count);
        List<Book> books = Book.find.all(FilterBuilder.create().ignoreSoftDelete());
        long start = System.currentTimeMillis();
        // Routes to PostgreSQL unnest array DELETE above worm.bulk-unnest-threshold (10 rows)
        orm.deleteAllBatch(books);
        return System.currentTimeMillis() - start;
    }

    // ── Select operations ──────────────────────────────────────────────────

    /** WORM unitary select by ID. */
    public long wormSelectById(int count) {
        wormBatchInsert(count);
        List<Book> books = Book.find.all();
        long start = System.currentTimeMillis();
        for (Book book : books) {
            Book.find.byId(book.getId());
        }
        return System.currentTimeMillis() - start;
    }

    /** WORM select all. */
    public long wormSelectAll(int count) {
        wormBatchInsert(count);
        long start = System.currentTimeMillis();
        Book.find.all();
        return System.currentTimeMillis() - start;
    }

    /** WORM select filtered. */
    public long wormSelectFiltered(int count) {
        wormBatchInsert(count);
        long start = System.currentTimeMillis();
        orm.findAll(Book.class, FilterBuilder.create().eq("status", "AVAILABLE"));
        return System.currentTimeMillis() - start;
    }

    // ── JPA: Standard paths ────────────────────────────────────────────────

    @Transactional
    public long jpaUnitaryInsert(int count) {
        cleanDatabaseJpa();
        long start = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            JpaAuthor author = JpaAuthor.builder()
                .id(UUID.randomUUID())
                .name("Author " + i)
                .email("author" + i + "@test.com")
                .build();
            jpaAuthorRepository.save(author);
            entityManager.flush();
            entityManager.clear();
            
            JpaBook book = JpaBook.builder()
                .id(UUID.randomUUID())
                .title("Book " + i)
                .isbn("ISBN-" + i)
                .status("AVAILABLE")
                .authorId(author.getId())
                .active(true)
                .build();
            jpaBookRepository.save(book);
            entityManager.flush();
            entityManager.clear();
        }
        return System.currentTimeMillis() - start;
    }

    @Transactional
    public long jpaBatchInsert(int count) {
        cleanDatabaseJpa();
        List<JpaAuthor> authors = new ArrayList<>();
        List<JpaBook> books = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            JpaAuthor author = JpaAuthor.builder()
                .id(UUID.randomUUID())
                .name("Author " + i)
                .email("author" + i + "@test.com")
                .build();
            authors.add(author);
            
            JpaBook book = JpaBook.builder()
                .id(UUID.randomUUID())
                .title("Book " + i)
                .isbn("ISBN-" + i)
                .status("AVAILABLE")
                .authorId(author.getId())
                .active(true)
                .build();
            books.add(book);
        }
        
        long start = System.currentTimeMillis();
        jpaAuthorRepository.saveAll(authors);
        entityManager.flush();
        entityManager.clear();
        
        jpaBookRepository.saveAll(books);
        entityManager.flush();
        entityManager.clear();
        return System.currentTimeMillis() - start;
    }

    @Transactional
    public long jpaUnitaryUpdate(int count) {
        jpaBatchInsert(count);
        List<JpaBook> books = jpaBookRepository.findAll();
        
        long start = System.currentTimeMillis();
        for (JpaBook book : books) {
            book.setTitle(book.getTitle() + " - Updated");
            jpaBookRepository.save(book);
            entityManager.flush();
            entityManager.clear();
        }
        return System.currentTimeMillis() - start;
    }

    @Transactional
    public long jpaBatchUpdate(int count) {
        jpaBatchInsert(count);
        List<JpaBook> books = jpaBookRepository.findAll();
        for (JpaBook book : books) {
            book.setTitle(book.getTitle() + " - Updated");
        }
        long start = System.currentTimeMillis();
        jpaBookRepository.saveAll(books);
        entityManager.flush();
        entityManager.clear();
        return System.currentTimeMillis() - start;
    }

    @Transactional
    public long jpaUnitaryDelete(int count) {
        jpaBatchInsert(count);
        List<JpaBook> books = jpaBookRepository.findAll();
        long start = System.currentTimeMillis();
        for (JpaBook book : books) {
            book.setActive(false);
            book.setDeletedAt(LocalDateTime.now());
            jpaBookRepository.save(book);
            entityManager.flush();
            entityManager.clear();
        }
        return System.currentTimeMillis() - start;
    }

    @Transactional
    public long jpaBatchDelete(int count) {
        jpaBatchInsert(count);
        List<JpaBook> books = jpaBookRepository.findAll();
        for (JpaBook book : books) {
            book.setActive(false);
            book.setDeletedAt(LocalDateTime.now());
        }
        long start = System.currentTimeMillis();
        jpaBookRepository.saveAll(books);
        entityManager.flush();
        entityManager.clear();
        return System.currentTimeMillis() - start;
    }

    @Transactional
    public long jpaSelectById(int count) {
        jpaBatchInsert(count);
        List<UUID> ids = jpaBookRepository.findAll().stream().map(JpaBook::getId).collect(Collectors.toList());
        entityManager.clear();
        long start = System.currentTimeMillis();
        for (UUID id : ids) {
            jpaBookRepository.findById(id);
            entityManager.clear();
        }
        return System.currentTimeMillis() - start;
    }

    @Transactional
    public long jpaSelectAll(int count) {
        jpaBatchInsert(count);
        entityManager.clear();
        long start = System.currentTimeMillis();
        jpaBookRepository.findAll();
        entityManager.clear();
        return System.currentTimeMillis() - start;
    }

    @Transactional
    public long jpaSelectFiltered(int count) {
        jpaBatchInsert(count);
        entityManager.clear();
        long start = System.currentTimeMillis();
        jpaBookRepository.findByStatus("AVAILABLE");
        entityManager.clear();
        return System.currentTimeMillis() - start;
    }
    
    // ── Soft/Hard Delete Helpers (backwards compat) ──

    public long wormSoftDeleteUnitary(int count) {
        wormBatchInsert(count);
        List<Book> books = Book.find.all();
        long start = System.currentTimeMillis();
        for (Book book : books) {
            orm.delete(book);  // soft delete
        }
        return System.currentTimeMillis() - start;
    }

    @Transactional
    public long jpaSoftDeleteUnitary(int count) {
        jpaBatchInsert(count);
        List<JpaBook> books = jpaBookRepository.findAll();
        long start = System.currentTimeMillis();
        for (JpaBook book : books) {
            book.setActive(false);
            book.setDeletedAt(LocalDateTime.now());
            jpaBookRepository.save(book);
            entityManager.flush();
            entityManager.clear();
        }
        return System.currentTimeMillis() - start;
    }
    
    public long wormHardDeleteUnitary(int count) {
        wormBatchInsert(count);
        List<Book> books = Book.find.all(FilterBuilder.create().ignoreSoftDelete());
        long start = System.currentTimeMillis();
        for (Book book : books) {
            jdbcTemplate.update("delete from books where id = ?", book.getId().toString());
        }
        return System.currentTimeMillis() - start;
    }

    @Transactional
    public long jpaHardDeleteUnitary(int count) {
        jpaBatchInsert(count);
        List<JpaBook> books = jpaBookRepository.findAll();
        long start = System.currentTimeMillis();
        for (JpaBook book : books) {
            jpaBookRepository.delete(book);
            entityManager.flush();
            entityManager.clear();
        }
        return System.currentTimeMillis() - start;
    }

    public long wormHardDeleteBatch(int count) {
        wormBatchInsert(count);
        long start = System.currentTimeMillis();
        jdbcTemplate.update("delete from books");
        jdbcTemplate.update("delete from authors");
        return System.currentTimeMillis() - start;
    }
    
    @Transactional
    public long jpaHardDeleteBatch(int count) {
        jpaBatchInsert(count);
        long start = System.currentTimeMillis();
        jpaBookRepository.deleteAllInBatch();
        entityManager.flush();
        entityManager.clear();
        jpaAuthorRepository.deleteAllInBatch();
        entityManager.flush();
        entityManager.clear();
        return System.currentTimeMillis() - start;
    }
    
    public long wormSoftDeleteBatch(int count) {
        wormBatchInsert(count);
        List<Book> books = Book.find.all();
        for (Book book : books) {
            book.setActive(false);
            book.setDeletedAt(LocalDateTime.now());
        }
        long start = System.currentTimeMillis();
        orm.updateAllBatch(books);
        return System.currentTimeMillis() - start;
    }
    
    @Transactional
    public long jpaSoftDeleteBatch(int count) {
        jpaBatchInsert(count);
        List<JpaBook> books = jpaBookRepository.findAll();
        for(JpaBook b : books) {
            b.setActive(false);
            b.setDeletedAt(LocalDateTime.now());
        }
        long start = System.currentTimeMillis();
        jpaBookRepository.saveAll(books);
        entityManager.flush();
        entityManager.clear();
        return System.currentTimeMillis() - start;
    }
}
