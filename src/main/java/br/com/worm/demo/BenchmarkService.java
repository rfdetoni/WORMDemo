package br.com.worm.demo;

import br.com.liviacare.worm.api.Deletable;
import br.com.liviacare.worm.api.Persistable;
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

@Service
@RequiredArgsConstructor
public class BenchmarkService {

    private final JpaAuthorRepository jpaAuthorRepository;
    private final JpaBookRepository jpaBookRepository;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;

    private String generateRandomString(int length) {
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            int index = (int) (Math.random() * characters.length());
            sb.append(characters.charAt(index));
        }
        return sb.toString();
    }

    @Transactional
    public void cleanDatabase() {
        Deletable.deleteAll(Book.find.all());
        Deletable.deleteAll(Author.find.all());
    }

    @Transactional
    public void cleanDatabaseJpa() {
        jpaBookRepository.deleteAll();
        jpaAuthorRepository.deleteAll();
        entityManager.flush();
        entityManager.clear();
    }
    
    // WORM Unitary Insert
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
    
    // JPA Unitary Insert
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

    // WORM Batch Insert
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
        Persistable.saveAll(authors);
        Persistable.saveAll(books);
        return System.currentTimeMillis() - start;
    }

    // JPA Batch Insert (Simulated with saveAll and clear)
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

    // Unitary Updates
    public long wormUnitaryUpdate(int count) {
        wormBatchInsert(count);
        List<String> ids = jdbcTemplate.queryForList("select id from books", String.class);
        long start = System.currentTimeMillis();
        for (String id : ids) {
            jdbcTemplate.update("update books set title = concat(title, ' - Updated'), updated_at = now() where id = ?", id);
        }
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

    // Batch Updates WORM
    public long wormBatchUpdate(int count) {
        wormBatchInsert(count);
        long start = System.currentTimeMillis();
        jdbcTemplate.update("update books set title = concat(title, ' - Updated'), updated_at = now() where 1=1");
        return System.currentTimeMillis() - start;
    }

    // JPA Batch Update
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

    // Unitary Select By ID
    public long wormSelectById(int count) {
        wormBatchInsert(count);
        List<String> ids = jdbcTemplate.queryForList("select id from books", String.class);
        long start = System.currentTimeMillis();
        for (String id : ids) {
            jdbcTemplate.queryForList("select * from books where id = ?", id);
        }
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

    // Select All
    public long wormSelectAll(int count) {
        wormBatchInsert(count);
        long start = System.currentTimeMillis();
        jdbcTemplate.queryForList("select * from books");
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

    // Select Filtered
    public long wormSelectFiltered(int count) {
        wormBatchInsert(count);
        long start = System.currentTimeMillis();
        jdbcTemplate.queryForList("select * from books where status = ?", "AVAILABLE");
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
    
    // Soft Delete Unitary
    public long wormSoftDeleteUnitary(int count) {
        wormBatchInsert(count);
        List<String> ids = jdbcTemplate.queryForList("select id from books", String.class);
        long start = System.currentTimeMillis();
        for (String id : ids) {
            jdbcTemplate.update("update books set active = false, deleted_at = now() where id = ?", id);
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
    
    // Hard Delete Unitary
    public long wormHardDeleteUnitary(int count) {
        wormBatchInsert(count);
        List<String> ids = jdbcTemplate.queryForList("select id from books", String.class);
        long start = System.currentTimeMillis();
        for (String id : ids) {
            jdbcTemplate.update("delete from books where id = ?", id);
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

    // Hard Delete Batch WORM
    public long wormHardDeleteBatch(int count) {
        wormBatchInsert(count);
        long start = System.currentTimeMillis();
        jdbcTemplate.update("delete from books");
        jdbcTemplate.update("delete from authors");
        return System.currentTimeMillis() - start;
    }
    
    // Hard Delete Batch JPA
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
        long start = System.currentTimeMillis();
        jdbcTemplate.update("update books set active = false, deleted_at = now()");
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
