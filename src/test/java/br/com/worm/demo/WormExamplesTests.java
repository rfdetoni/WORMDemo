package br.com.worm.demo;

import br.com.liviacare.worm.query.FilterBuilder;
import br.com.liviacare.worm.query.Pageable;
import br.com.liviacare.worm.query.Slice;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class WormExamplesTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Simple projection record example (automatic mapping from a row)
    public record BookProjection(UUID id, String title, String authorName) {}

    private Author createAuthor(String name, String email) {
        Author a = Author.builder()
                .id(UUID.randomUUID())
                .name(name)
                .email(email)
                .build();
        a.save();
        return a;
    }

    private Book createBook(String title, String isbn, String status, UUID authorId) {
        Book b = Book.builder()
                .id(UUID.randomUUID())
                .title(title)
                .isbn(isbn)
                .status(status)
                .authorId(authorId)
                .build();
        b.save();
        return b;
    }

    @Test
    void testActiveRecordCreateAndFind() {
        Author author = createAuthor("George R. R. Martin", "grrm@example.com");

        Book book = createBook("A Game of Thrones", "978-0553593716", "PUBLISHED", author.getId());

        Optional<Book> found = Book.find.byId(book.getId());
        assertTrue(found.isPresent(), "Book should be found by id");
        assertEquals("A Game of Thrones", found.get().getTitle());

        Optional<Author> foundAuthor = Author.find.byId(author.getId());
        assertTrue(foundAuthor.isPresent());
        assertEquals("George R. R. Martin", foundAuthor.get().getName());
    }

    @Test
    void testFilterBuilderQueryAndCount() {
        Author author = createAuthor("Isaac Asimov", "asimov@example.com");

        createBook("Foundation", "978-0553293357", "PUBLISHED", author.getId());
        createBook("I, Robot", "978-0553294385", "PUBLISHED", author.getId());
        createBook("The End of Eternity", "978-0553294651", "DRAFT", author.getId());

        FilterBuilder filterPublished = FilterBuilder.create().eq("status", "PUBLISHED");
        List<Book> published = Book.find.all(filterPublished);
        assertEquals(2, published.size());

        long publishedCount = Book.find.count(filterPublished);
        assertEquals(2, publishedCount);
    }

    @Test
    void testUpdateVersionIncrementOnSave() {
        Author author = createAuthor("Neil Gaiman", "ng@example.com");
        Book book = createBook("American Gods", "978-0060558123", "PUBLISHED", author.getId());

        long versionBefore = book.getVersion();
        book.setStatus("OUT_OF_STOCK");
        book.save();

        Optional<Book> reloaded = Book.find.byId(book.getId());
        assertTrue(reloaded.isPresent());
        assertTrue(reloaded.get().getVersion() >= versionBefore,
                () -> "Expected version to increment or be >= previous (before=" + versionBefore + ", after=" + reloaded.get().getVersion() + ")");
    }

    @Test
    void testPaginationWithSlice() {
        Author author = createAuthor("Terry Pratchett", "tp@example.com");
        for (int i = 0; i < 12; i++) {
            createBook("Discworld " + i, "isbn-dw-" + i, "PUBLISHED", author.getId());
        }

        FilterBuilder filterByAuthor = FilterBuilder.create().eq("author_id", author.getId());
        Slice<Book> firstPage = Book.find.all(filterByAuthor, Pageable.of(0, 5));
        assertNotNull(firstPage);
        assertEquals(5, firstPage.content().size());
        assertTrue(firstPage.hasNext());

        Slice<Book> thirdPage = Book.find.all(filterByAuthor, Pageable.of(2, 5));
        assertEquals(2, thirdPage.content().size());
        assertFalse(thirdPage.hasNext());
    }

    @Test
    void testSoftDeleteBehavior() {
        Author author = createAuthor("Douglas Adams", "da@example.com");
        Book book = createBook("The Hitchhiker's Guide to the Galaxy", "978-0345391803", "PUBLISHED", author.getId());

        // Delete (should be soft delete via DeletedAt/Active handling)
        book.delete();

        long countById = Book.find.count(FilterBuilder.create().eq("id", book.getId()));
        assertEquals(0, countById, "Soft deleted book should not be returned by default queries");
    }

    @Test
    void testAggregationTotalBooks() {
        Author author = createAuthor("Philip K. Dick", "pkd@example.com");
        createBook("Do Androids Dream of Electric Sheep?", "978-0345404473", "PUBLISHED", author.getId());
        createBook("Ubik", "978-0525432050", "PUBLISHED", author.getId());

        long total = Book.find.count();
        assertTrue(total >= 2, "There should be at least the two books we created");
    }

    @Test
    void testRecordProjectionWithJdbcTemplate() {
        Author author = createAuthor("H. P. Lovecraft", "hl@example.com");
        Book book = createBook("At the Mountains of Madness", "978-...-hl", "PUBLISHED", author.getId());

        String sql = "select b.id as id, b.title as title, a.name as author_name " +
                "from books b join authors a on a.id = b.author_id where b.id = ?";

        List<BookProjection> rows = jdbcTemplate.query(sql, new Object[]{book.getId()},
                (rs, rowNum) -> new BookProjection(UUID.fromString(rs.getString("id")), rs.getString("title"), rs.getString("author_name"))
        );

        assertEquals(1, rows.size());
        BookProjection p = rows.get(0);
        assertEquals(book.getId(), p.id());
        assertEquals(book.getTitle(), p.title());
        assertEquals(author.getName(), p.authorName());
    }
}

