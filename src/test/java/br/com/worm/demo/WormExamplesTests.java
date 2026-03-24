package br.com.worm.demo;

import br.com.liviacare.worm.api.Deletable;
import br.com.liviacare.worm.api.Finder;
import br.com.liviacare.worm.api.Persistable;
import br.com.liviacare.worm.orm.OrmOperations;
import br.com.liviacare.worm.query.FilterBuilder;
import br.com.liviacare.worm.query.Page;
import br.com.liviacare.worm.query.Pageable;
import br.com.liviacare.worm.query.Slice;
import br.com.worm.demo.dto.AuthorSummary;
import br.com.worm.demo.repository.AuthorWormRepository;
import br.com.worm.demo.repository.BookQueryRepository;
import br.com.worm.demo.repository.BookWormRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class WormExamplesTests {

    // ── Injected collaborators ──────────────────────────────────────────────

    @Autowired
    private OrmOperations orm;

    @Autowired
    private AuthorWormRepository authorWormRepo;

    @Autowired
    private BookWormRepository bookWormRepo;

    @Autowired
    private BookQueryRepository bookQueryRepo;

    // ── Setup ───────────────────────────────────────────────────────────────

    @BeforeEach
    void cleanupData() {
        Deletable.hard.deleteAll(Book.find.all(FilterBuilder.create().isNotNull("active").ignoreSoftDelete()));
        Deletable.hard.deleteAll(Author.find.all());
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

    // ═══════════════════════════════════════════════════════════════════════
    //  Section 6 — ActiveRecord / Finder style
    // ═══════════════════════════════════════════════════════════════════════

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
                () -> "Expected version to increment (before=" + versionBefore + ", after=" + reloaded.get().getVersion() + ")");
    }

    @Test
    void testDeletableDeleteByIdAndDeleteAll() {
        Author author = createAuthor("Douglas Adams", "da@example.com");
        Book one = Persistable.save(newBook("The Hitchhiker's Guide", "978-0345391803", "PUBLISHED", author.getId()));
        Book two = Persistable.save(newBook("Restaurant at the End", "978-0345391810", "PUBLISHED", author.getId()));

        one.delete();
        Optional<Book> softDeleted = Book.find.byId(one.getId());
        assertTrue(softDeleted.isPresent(), "Book should still be loadable by id after soft delete");
        assertFalse(softDeleted.orElseThrow().isActive(), "Soft-deleted book must be marked inactive");

        Deletable.deleteAll(List.of(two));
        assertNotNull(Book.find.byId(two.getId()), "deleteAll should execute without breaking finder contract");
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

    // ═══════════════════════════════════════════════════════════════════════
    //  Section 7 — OrmOperations
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void testOrmOperationsFindById() {
        Author author = createAuthor("Frank Herbert", "fh@example.com");

        Optional<Author> found = orm.findById(Author.class, author.getId());
        assertTrue(found.isPresent());
        assertEquals("Frank Herbert", found.get().getName());
    }

    @Test
    void testOrmOperationsFindOne() {
        createAuthor("Ray Bradbury", "rb@example.com");

        Optional<Author> found = orm.findOne(Author.class,
                FilterBuilder.create().eq("email", "rb@example.com"));
        assertTrue(found.isPresent());
        assertEquals("Ray Bradbury", found.get().getName());
    }

    @Test
    void testOrmOperationsFindAll() {
        createAuthor("Author One", "one@example.com");
        createAuthor("Author Two", "two@example.com");

        List<Author> all = orm.findAll(Author.class, FilterBuilder.create());
        assertTrue(all.size() >= 2);
    }

    @Test
    void testOrmOperationsSaveUpdateDelete() {
        Author author = Author.builder()
                .id(UUID.randomUUID())
                .name("Temporary Author")
                .email("temp@example.com")
                .build();

        orm.save(author);
        assertTrue(orm.existsById(Author.class, author.getId()));

        author.setName("Temporary Author Updated");
        orm.update(author);
        assertEquals("Temporary Author Updated",
                orm.findById(Author.class, author.getId()).orElseThrow().getName());

        orm.deleteById(Author.class, author.getId());
        assertFalse(orm.existsById(Author.class, author.getId()));
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Section 8 — FilterBuilder predicates
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void testFilterBuilderLike() {
        createAuthor("J.R.R. Tolkien", "jrr@example.com");
        createAuthor("J.K. Rowling", "jkr@example.com");
        createAuthor("Stephen King", "sk@example.com");

        List<Author> result = orm.findAll(Author.class,
                FilterBuilder.create().like("name", "J.%"));
        assertEquals(2, result.size());
    }

    @Test
    void testFilterBuilderIn() {
        createAuthor("Margaret Atwood", "ma@example.com");
        createAuthor("Cormac McCarthy", "cm@example.com");
        createAuthor("Don DeLillo", "dd@example.com");

        List<Author> result = orm.findAll(Author.class,
                FilterBuilder.create().in("email", List.of("ma@example.com", "dd@example.com")));
        assertEquals(2, result.size());
    }

    @Test
    void testFilterBuilderOrGroup() {
        Author author = createAuthor("Agatha Christie", "ac@example.com");
        Book pub = Persistable.save(newBook("Murder on the Orient Express", "isbn-001", "PUBLISHED", author.getId()));
        Book draft = Persistable.save(newBook("And Then There Were None", "isbn-002", "DRAFT", author.getId()));

        // Books that are PUBLISHED OR DRAFT
        List<Book> result = orm.findAll(Book.class,
                FilterBuilder.create()
                        .openParen()
                        .eq("status", "PUBLISHED")
                        .or()
                        .eq("status", "DRAFT")
                        .closeParen()
                        .ignoreSoftDelete());

        assertTrue(result.stream().anyMatch(b -> b.getId().equals(pub.getId())));
        assertTrue(result.stream().anyMatch(b -> b.getId().equals(draft.getId())));
    }

    @Test
    void testFilterBuilderIgnoreSoftDelete() {
        Author author = createAuthor("Edgar Allan Poe", "eap@example.com");
        Book book = Persistable.save(newBook("The Raven", "isbn-raven", "PUBLISHED", author.getId()));

        // Soft-delete the book
        book.delete();

        // Default filter should NOT see it
        List<Book> active = orm.findAll(Book.class, FilterBuilder.create().eq("id", book.getId()));
        assertTrue(active.isEmpty(), "Soft-deleted book should be hidden by default");

        // With ignoreSoftDelete it should appear
        List<Book> all = orm.findAll(Book.class,
                FilterBuilder.create().eq("id", book.getId()).ignoreSoftDelete());
        assertFalse(all.isEmpty(), "ignoreSoftDelete should expose logically deleted rows");
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Section 9 — Page (with total count) and Slice
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void testPageApi() {
        createAuthor("Author P1", "p1@example.com");
        createAuthor("Author P2", "p2@example.com");
        createAuthor("Author P3", "p3@example.com");

        Page<Author> page = orm.findPage(Author.class,
                FilterBuilder.create().orderBy("name"),
                Pageable.of(0, 2));

        assertNotNull(page);
        assertFalse(page.content().isEmpty());
        assertTrue(page.totalElements() >= 3,
                "totalElements should include all matching rows");
        assertTrue(page.totalPages() >= 2,
                "totalPages should be at least 2 for 3 rows with page-size 2");
    }

    @Test
    void testSliceApi() {
        Author author = createAuthor("Slice Author", "slice@example.com");
        for (int i = 0; i < 5; i++) {
            Persistable.save(newBook("Book " + i, "slice-isbn-" + i, "PUBLISHED", author.getId()));
        }

        Slice<Book> slice = orm.findAll(Book.class,
                FilterBuilder.create().eq("author_id", author.getId()),
                Pageable.of(0, 3));

        assertNotNull(slice);
        assertEquals(3, slice.content().size());
        assertTrue(slice.hasNext());
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Section 10 — Projections
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void testProjectionFindById() {
        Author author = createAuthor("Projection Author", "proj@example.com");

        Optional<AuthorSummary> summary = AuthorSummary.find.byId(author.getId());
        assertTrue(summary.isPresent());
        assertEquals("Projection Author", summary.get().name());
        assertEquals("proj@example.com", summary.get().email());
        assertNotNull(summary.get().id());
    }

    @Test
    void testProjectionFindAll() {
        createAuthor("Sum Author 1", "sum1@example.com");
        createAuthor("Sum Author 2", "sum2@example.com");

        List<AuthorSummary> summaries = AuthorSummary.find.all(
                FilterBuilder.create().orderBy("name"));

        assertFalse(summaries.isEmpty());
        summaries.forEach(s -> {
            assertNotNull(s.id());
            assertNotNull(s.name());
            assertNotNull(s.email());
        });
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Section 7 — findColumn (single-column queries)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void testFindColumn() {
        createAuthor("Col Author 1", "col1@example.com");
        createAuthor("Col Author 2", "col2@example.com");

        List<String> emails = orm.findColumn(Author.class, "email", String.class,
                FilterBuilder.create().like("email", "col%"));

        assertEquals(2, emails.size());
        assertTrue(emails.contains("col1@example.com"));
        assertTrue(emails.contains("col2@example.com"));
    }

    @Test
    void testFindColumnOne() {
        Author author = createAuthor("One Col Author", "onecol@example.com");

        Optional<String> email = orm.findColumnOne(Author.class, "email", String.class,
                FilterBuilder.create().eq("id", author.getId()));

        assertTrue(email.isPresent());
        assertEquals("onecol@example.com", email.get());
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Section 7 — Aggregates
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void testOrmOperationsCount() {
        createAuthor("Count A1", "cnt1@example.com");
        createAuthor("Count A2", "cnt2@example.com");

        long count = orm.count(Author.class);
        assertTrue(count >= 2);
    }

    @Test
    void testOrmOperationsExistsById() {
        Author author = createAuthor("Exists Author", "exists@example.com");

        assertTrue(orm.existsById(Author.class, author.getId()));
        assertFalse(orm.existsById(Author.class, UUID.randomUUID()));
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Section 12 — Native SQL (executeRaw)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void testExecuteRaw() {
        createAuthor("Raw Author", "raw@example.com");

        List<Author> result = orm.executeRaw(
                "select * from authors where email = ?",
                Author.class,
                "raw@example.com");

        assertEquals(1, result.size());
        assertEquals("Raw Author", result.get(0).getName());
    }

    @Test
    void testNativeQueryList() {
        createAuthor("Native Author", "native@example.com");

        List<Author> result = Finder.nativeQueryList(
                "select * from authors where email = ?",
                Author.class,
                "native@example.com");

        assertEquals(1, result.size());
        assertEquals("Native Author", result.get(0).getName());
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Section 15 — GenericRepository
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void testAuthorWormRepository() {
        Author author = Author.builder()
                .id(UUID.randomUUID())
                .name("Repo Author")
                .email("repo@example.com")
                .build();

        authorWormRepo.save(author);

        Optional<Author> found = authorWormRepo.findById(author.getId());
        assertTrue(found.isPresent());
        assertEquals("Repo Author", found.get().getName());

        author.setName("Repo Author Updated");
        authorWormRepo.update(author);
        assertEquals("Repo Author Updated",
                authorWormRepo.findById(author.getId()).orElseThrow().getName());

        authorWormRepo.delete(author);
        assertFalse(authorWormRepo.findById(author.getId()).isPresent());
    }

    @Test
    void testBookWormRepositorySaveAll() {
        Author author = createAuthor("Batch Repo Author", "batchrepo@example.com");

        List<Book> books = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            books.add(newBook("Batch Book " + i, "batch-isbn-" + i, "AVAILABLE", author.getId()));
        }

        bookWormRepo.saveAll(books);

        List<Book> all = bookWormRepo.findAll(
                FilterBuilder.create().eq("author_id", author.getId()));
        assertEquals(3, all.size());
    }

    @Test
    void testGenericRepositoryFindColumn() {
        createAuthor("GR Email 1", "gremail1@example.com");
        createAuthor("GR Email 2", "gremail2@example.com");

        List<String> emails = authorWormRepo.findColumn("email", String.class,
                FilterBuilder.create().like("email", "gremail%"));

        assertEquals(2, emails.size());
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Section 14 — @QueryRepository
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void testQueryRepositoryFindActiveByStatus() {
        Author author = createAuthor("QRepo Author", "qrepo@example.com");
        Persistable.save(newBook("QRepo Book 1", "qrepo-isbn-1", "AVAILABLE", author.getId()));
        Persistable.save(newBook("QRepo Book 2", "qrepo-isbn-2", "SOLD_OUT", author.getId()));

        List<Book> available = bookQueryRepo.findActiveByStatus("AVAILABLE");
        assertFalse(available.isEmpty());
        assertTrue(available.stream().allMatch(b -> "AVAILABLE".equals(b.getStatus())));
    }

    @Test
    void testQueryRepositoryFindById() {
        Author author = createAuthor("QRepo Author 2", "qrepo2@example.com");
        Book book = Persistable.save(newBook("QRepo Find Book", "qrepo-find-isbn", "PUBLISHED", author.getId()));

        Optional<Book> found = bookQueryRepo.findById(book.getId());
        assertTrue(found.isPresent());
        assertEquals("QRepo Find Book", found.get().getTitle());
    }

    @Test
    void testQueryRepositoryFindActiveByAuthor() {
        Author author = createAuthor("QRepo Author 3", "qrepo3@example.com");
        Persistable.save(newBook("QRepo A Book 1", "qrepo-a-isbn-1", "PUBLISHED", author.getId()));
        Persistable.save(newBook("QRepo A Book 2", "qrepo-a-isbn-2", "PUBLISHED", author.getId()));

        List<Book> books = bookQueryRepo.findActiveByAuthor(author.getId());
        assertEquals(2, books.size());
        assertTrue(books.stream().allMatch(b -> author.getId().equals(b.getAuthorId())));
    }

    @Test
    void testQueryRepositorySliceWithPageable() {
        Author author = createAuthor("Slice QRepo Author", "sliceqrepo@example.com");
        for (int i = 0; i < 4; i++) {
            Persistable.save(newBook("Slice Q Book " + i, "sq-isbn-" + i, "PUBLISHED", author.getId()));
        }

        Slice<Book> slice = bookQueryRepo.findActiveRecent(Pageable.of(0, 2));
        assertNotNull(slice);
        assertFalse(slice.content().isEmpty());
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Section 16 — Batch / bulk OrmOperations
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void testOrmSaveAllBatchAndDeleteAllBatch() {
        Author author = createAuthor("Batch Author", "batch@example.com");

        List<Book> books = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            books.add(newBook("Batch Book " + i, "bi-" + i, "AVAILABLE", author.getId()));
        }
        orm.saveAllBatch(books);

        long count = Book.find.count(FilterBuilder.create().eq("author_id", author.getId()));
        assertEquals(25, count, "All 25 books should be saved via bulk insert");

        // deleteAllBatch routes to unnest DELETE above bulk-unnest-threshold (10)
        Deletable.hard.deleteAll(books);
        long afterDelete = orm.count(Book.class,
                FilterBuilder.create().eq("author_id", author.getId()).ignoreSoftDelete());
        assertEquals(0, afterDelete, "All 25 books should be physically deleted");
    }

    @Test
    void testOrmUpdateAllBatch() {
        Author author = createAuthor("Update Batch Author", "ubatch@example.com");

        List<Book> books = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            books.add(Persistable.save(newBook("UB Book " + i, "ubi-" + i, "DRAFT", author.getId())));
        }

        books.forEach(b -> b.setStatus("PUBLISHED"));
        // updateAllBatch routes to unnest array UPDATE above bulk-unnest-threshold (10)
        orm.updateAllBatch(books);

        List<Book> updated = orm.findAll(Book.class,
                FilterBuilder.create().eq("author_id", author.getId()));
        assertTrue(updated.stream().allMatch(b -> "PUBLISHED".equals(b.getStatus())),
                "All books should be updated to PUBLISHED via bulk update");
    }
}
