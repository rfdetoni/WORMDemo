package br.com.worm.demo.repository;

import br.com.worm.demo.JpaBook;
import br.com.worm.demo.JpaBookRepository;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class BookRepository {

    private final JpaBookRepository jpa;
    private final EntityManager entityManager;

    public BookRepository(JpaBookRepository jpa, EntityManager entityManager) {
        this.jpa = jpa;
        this.entityManager = entityManager;
    }

    private void flushAndClear() {
        jpa.flush();
        entityManager.clear();
    }

    @Transactional
    public JpaBook save(JpaBook b) {
        JpaBook saved = jpa.save(b);
        flushAndClear();
        return saved;
    }

    @Transactional
    public List<JpaBook> saveAll(List<JpaBook> books) {
        List<JpaBook> saved = jpa.saveAll(books);
        flushAndClear();
        return saved;
    }

    @Transactional
    public void delete(JpaBook b) {
        jpa.delete(b);
        flushAndClear();
    }

    @Transactional
    public Optional<JpaBook> findById(UUID id) {
        Optional<JpaBook> found = jpa.findById(id);
        flushAndClear();
        return found;
    }

    @Transactional
    public List<JpaBook> findAll() {
        List<JpaBook> all = jpa.findAll();
        flushAndClear();
        return all;
    }

    @Transactional
    public void deleteAll() {
        jpa.deleteAll();
        flushAndClear();
    }

    @Transactional
    public void deleteAllInBatch() {
        jpa.deleteAllInBatch();
        flushAndClear();
    }

    @Transactional
    public List<JpaBook> findByStatus(String status) {
        List<JpaBook> books = jpa.findByStatus(status);
        flushAndClear();
        return books;
    }

    @Transactional
    public List<JpaBook> findByAuthorIdPaged(UUID authorId, int page, int size) {
        List<JpaBook> books = jpa.findByAuthorId(authorId, PageRequest.of(page, size)).getContent();
        flushAndClear();
        return books;
    }
}

