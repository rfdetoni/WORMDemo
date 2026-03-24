package br.com.worm.demo.repository;

import br.com.liviacare.worm.orm.OrmOperations;
import br.com.liviacare.worm.repository.GenericRepository;
import br.com.worm.demo.Book;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * WORM GenericRepository wrapper for Book (Section 15 — Pattern C).
 * Inherits: save, update, delete, findById, findAll, findAll(filter),
 *           findAll(filter, pageable), saveAll, findColumn, findColumnOne.
 */
@Repository
public class BookWormRepository extends GenericRepository<Book, UUID> {

    public BookWormRepository(OrmOperations orm) {
        super(Book.class, orm);
    }
}

