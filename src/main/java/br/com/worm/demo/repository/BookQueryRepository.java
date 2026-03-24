package br.com.worm.demo.repository;

import br.com.liviacare.worm.annotation.query.Query;
import br.com.liviacare.worm.annotation.query.QueryParam;
import br.com.liviacare.worm.annotation.query.QueryRepository;
import br.com.liviacare.worm.query.Pageable;
import br.com.liviacare.worm.query.Slice;
import br.com.worm.demo.Book;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Native-SQL query repository for Book (Section 14 — Pattern D).
 *
 * Detected automatically by WORM because the base-package
 * "br.com.worm.demo" is listed under worm.query.repository.base-packages.
 *
 * Supported return types: List<T>, Optional<T>, Slice<T> (with Pageable param).
 */
@QueryRepository
public interface BookQueryRepository {

    @Query("select * from books where active = true and status = :status order by created_at desc")
    List<Book> findActiveByStatus(@QueryParam("status") String status);

    @Query("select * from books where id = :id")
    Optional<Book> findById(@QueryParam("id") UUID id);

    @Query("select * from books where author_id = :authorId and active = true order by created_at desc")
    List<Book> findActiveByAuthor(@QueryParam("authorId") UUID authorId);

    @Query("select * from books where active = true order by created_at desc")
    Slice<Book> findActiveRecent(Pageable pageable);

    @Query("select count(*) from books where status = :status")
    Long countByStatus(@QueryParam("status") String status);
}

