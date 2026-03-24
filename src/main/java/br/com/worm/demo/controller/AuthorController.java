package br.com.worm.demo.controller;

import br.com.worm.demo.Author;
import br.com.worm.demo.dto.AuthorDto;
import br.com.worm.demo.dto.AuthorSummary;
import br.com.worm.demo.dto.CreateAuthorDto;
import br.com.worm.demo.mapper.AuthorMapper;
import br.com.worm.demo.service.AuthorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/authors")
public class AuthorController {

    private final AuthorService service;

    public AuthorController(AuthorService service) {
        this.service = service;
    }

    /** List all authors as full DTO. */
    @GetMapping
    public List<AuthorDto> list() {
        return service.findAll().stream().map(AuthorMapper::toDto).collect(Collectors.toList());
    }

    /**
     * Lightweight projection endpoint — returns only id, name, email.
     * Example: GET /api/authors/summaries
     */
    @GetMapping("/summaries")
    public List<AuthorSummary> summaries() {
        return service.findAllSummaries();
    }

    /**
     * Paginated endpoint with total-count metadata.
     * Example: GET /api/authors/page?page=0&size=10
     */
    @GetMapping("/page")
    public ResponseEntity<?> page(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        var result = service.findPage(page, size);
        return ResponseEntity.ok(result);
    }

    /**
     * Search by keyword (matches name OR email via OR-group filter).
     * Example: GET /api/authors/search?q=tolkien
     */
    @GetMapping("/search")
    public List<AuthorDto> search(@RequestParam String q) {
        return service.search(q).stream().map(AuthorMapper::toDto).collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AuthorDto> get(@PathVariable java.util.UUID id) {
        return service.findById(id)
                .map(AuthorMapper::toDto)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/summary")
    public ResponseEntity<AuthorSummary> getSummary(@PathVariable java.util.UUID id) {
        return service.findSummaryById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<AuthorDto> create(@RequestBody CreateAuthorDto body) {
        Author a = AuthorMapper.fromCreate(body.name(), body.email());
        service.save(a);
        return ResponseEntity.created(URI.create("/api/authors/" + a.getId()))
                .body(AuthorMapper.toDto(a));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable java.util.UUID id) {
        service.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
