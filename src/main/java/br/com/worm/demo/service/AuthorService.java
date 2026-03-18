package br.com.worm.demo.service;

import br.com.worm.demo.Author;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthorService {

    public Optional<Author> findById(UUID id) {
        return Author.find.byId(id);
    }

    public List<Author> findAll() {
        return Author.find.all();
    }

    @Transactional
    public Author save(Author author) {
        author.save();
        return author;
    }

    @Transactional
    public void deleteById(UUID id) {
        Author.find.byId(id).ifPresent(Author::delete);
    }
}

