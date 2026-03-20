package br.com.worm.demo.repository;

import br.com.worm.demo.JpaAuthor;
import br.com.worm.demo.JpaAuthorRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AuthorRepository {

    private final JpaAuthorRepository jpa;
    private final EntityManager entityManager;

    public AuthorRepository(JpaAuthorRepository jpa, EntityManager entityManager) {
        this.jpa = jpa;
        this.entityManager = entityManager;
    }

    private void flushAndClear() {
        jpa.flush();
        entityManager.clear();
    }

    @Transactional
    public JpaAuthor save(JpaAuthor a) {
        JpaAuthor saved = jpa.save(a);
        flushAndClear();
        return saved;
    }

    @Transactional
    public List<JpaAuthor> saveAll(List<JpaAuthor> authors) {
        List<JpaAuthor> saved = jpa.saveAll(authors);
        flushAndClear();
        return saved;
    }

    @Transactional
    public Optional<JpaAuthor> findById(UUID id) {
        Optional<JpaAuthor> found = jpa.findById(id);
        flushAndClear();
        return found;
    }

    @Transactional
    public List<JpaAuthor> findAll() {
        List<JpaAuthor> all = jpa.findAll();
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
}

