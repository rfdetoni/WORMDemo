package br.com.worm.demo.controller;

import br.com.worm.demo.dto.BookDto;
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

    @GetMapping()
    public ResponseEntity<Iterable<BookDto>> list() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookDto> get(@PathVariable UUID id) {
        var bookDto = service.findById(id);
        return ResponseEntity.ok(bookDto);
    }

    @PostMapping
    public ResponseEntity<Void> create(@RequestBody br.com.worm.demo.dto.CreateBookDto body) {
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

