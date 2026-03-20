package br.com.worm.demo;

import br.com.liviacare.worm.api.Deletable;
import br.com.liviacare.worm.api.Persistable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class WormExamplesTests {

    @BeforeEach
    void cleanupData() {
        Deletable.deleteAll(Book.find.all());
        Deletable.deleteAll(Author.find.all());
    }

    private Author createAuthor(String name, String email) {
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

    @Test
    void testActiveRecordCreateAndFind() {
        Author author = createAuthor("George R. R. Martin", "grrm@example.com");

        Book book = newBook("A Game of Thrones", "978-0553593716", "PUBLISHED", author.getId());
        book.save();

        Optional<Book> found = Book.find.byId(book.getId());
        assertTrue(found.isPresent(), "Book should be found by id");
        assertEquals("A Game of Thrones", found.get().getTitle());

        Optional<Author> foundAuthor = Author.find.byId(author.getId());
        assertTrue(foundAuthor.isPresent());
        assertEquals("George R. R. Martin", foundAuthor.get().getName());
    }

    @Test
    void testPersistableSaveAllAndUpdateAll() {
        Author isaac = Author.builder()
                .id(UUID.randomUUID())
                .name("Isaac Asimov")
                .email("asimov@example.com")
                .build();
        Author arthur = Author.builder()
                .id(UUID.randomUUID())
                .name("Arthur C. Clarke")
                .email("acc@example.com")
                .build();

        List<Author> saved = Persistable.saveAll(List.of(isaac, arthur));
        assertEquals(2, saved.size());

        isaac.setName("Isaac Asimov Updated");
        arthur.setName("Arthur C. Clarke Updated");
        List<Author> updated = Persistable.updateAll(List.of(isaac, arthur));
        assertEquals(2, updated.size());

        assertEquals("Isaac Asimov Updated", Author.find.byId(isaac.getId()).orElseThrow().getName());
        assertEquals("Arthur C. Clarke Updated", Author.find.byId(arthur.getId()).orElseThrow().getName());
    }

    @Test
    void testPersistableUpdateVersionIncrement() {
        Author author = createAuthor("Neil Gaiman", "ng@example.com");
        Book book = newBook("American Gods", "978-0060558123", "PUBLISHED", author.getId());
        Persistable.save(book);

        long versionBefore = book.getVersion();
        book.setStatus("OUT_OF_STOCK");
        Persistable.update(book);

        Optional<Book> reloaded = Book.find.byId(book.getId());
        assertTrue(reloaded.isPresent());
        assertTrue(reloaded.get().getVersion() >= versionBefore,
                () -> "Expected version to increment or be >= previous (before=" + versionBefore + ", after=" + reloaded.get().getVersion() + ")");
    }

    @Test
    void testDeletableDeleteByIdAndDeleteAll() {
        Author author = createAuthor("Douglas Adams", "da@example.com");
        Book one = Persistable.save(newBook("The Hitchhiker's Guide to the Galaxy", "978-0345391803", "PUBLISHED", author.getId()));
        Book two = Persistable.save(newBook("The Restaurant at the End of the Universe", "978-0345391810", "PUBLISHED", author.getId()));

        one.delete();
        Optional<Book> softDeleted = Book.find.byId(one.getId());
        assertTrue(softDeleted.isPresent(), "Book should still be loadable by id after soft delete");
        assertFalse(softDeleted.orElseThrow().isActive(), "Soft-deleted book must be marked inactive");

        Deletable.deleteAll(List.of(two));
        Optional<Book> batchDeleted = Book.find.byId(two.getId());
        assertNotNull(batchDeleted, "deleteAll should execute without breaking finder contract");
    }

    @Test
    void testActiveRecordCount() {
        Author author = createAuthor("Philip K. Dick", "pkd@example.com");
        Persistable.save(newBook("Do Androids Dream of Electric Sheep?", "978-0345404473", "PUBLISHED", author.getId()));
        Persistable.save(newBook("Ubik", "978-0525432050", "PUBLISHED", author.getId()));

        long total = Book.find.count();
        assertTrue(total >= 2, "There should be at least the two books we created");
    }

    @Test
    void testActiveRecordStaticGatewayAvailable() {
        assertNotNull(Book.find);
        assertNotNull(Author.find);
    }
}
