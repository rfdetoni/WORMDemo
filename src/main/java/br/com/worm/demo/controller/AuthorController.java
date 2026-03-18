package br.com.worm.demo.controller;

import br.com.worm.demo.Author;
import br.com.worm.demo.dto.AuthorDto;
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

    @GetMapping
    public List<AuthorDto> list() {
        return service.findAll().stream().map(AuthorMapper::toDto).collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AuthorDto> get(@PathVariable java.util.UUID id) {
        return service.findById(id).map(AuthorMapper::toDto).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<AuthorDto> create(@RequestBody CreateAuthorDto body) {
        Author a = AuthorMapper.fromCreate(body.name(), body.email());
        a.save();
        AuthorDto dto = AuthorMapper.toDto(a);
        return ResponseEntity.created(URI.create("/api/authors/" + a.getId())).body(dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable java.util.UUID id) {
        service.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}

