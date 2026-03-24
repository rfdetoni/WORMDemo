package br.com.worm.demo.controller;

import br.com.liviacare.worm.query.FilterBuilder;
import br.com.liviacare.worm.query.Pageable;
import br.com.worm.demo.dto.BookDto;
import br.com.worm.demo.dto.CreateBookDto;
import br.com.worm.demo.service.BookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookService service;

    public BookController(BookService service) {
        this.service = service;
    }

    /** List all active books (BookDto projection with author join). */
    @GetMapping
    public ResponseEntity<Iterable<BookDto>> list() {
        return ResponseEntity.ok(service.findAll());
    }

    /**
     * Paginated with total-count metadata.
     * Example: GET /api/books/page?page=0&size=10
     */
    @GetMapping("/page")
    public ResponseEntity<?> page(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        var result = service.findPage(FilterBuilder.create().orderBy("created_at", false),
                Pageable.of(page, size));
        return ResponseEntity.ok(result);
    }

    /**
     * Admin endpoint: includes logically deleted books.
     * Example: GET /api/books/all-including-deleted
     */
    @GetMapping("/all-including-deleted")
    public ResponseEntity<?> allIncludingDeleted() {
        return ResponseEntity.ok(service.findIncludingDeleted());
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookDto> get(@PathVariable UUID id) {
        var bookDto = service.findById(id);
        if (bookDto == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(bookDto);
    }

    @PostMapping
    public ResponseEntity<Void> create(@RequestBody CreateBookDto body) {
        if (body.title() == null || body.isbn() == null || body.authorId() == null) {
            return ResponseEntity.badRequest().build();
        }
        service.create(body.title(), body.isbn(), body.status(), body.authorId());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
