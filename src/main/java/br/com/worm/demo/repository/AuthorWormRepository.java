package br.com.worm.demo.repository;

import br.com.liviacare.worm.orm.OrmOperations;
import br.com.liviacare.worm.repository.GenericRepository;
import br.com.worm.demo.Author;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * WORM GenericRepository wrapper for Author (Section 15 — Pattern C).
 * Inherits: save, update, delete, findById, findAll, findAll(filter),
 *           findAll(filter, pageable), saveAll, findColumn, findColumnOne.
 */
@Repository
public class AuthorWormRepository extends GenericRepository<Author, UUID> {

    public AuthorWormRepository(OrmOperations orm) {
        super(Author.class, orm);
    }
}

