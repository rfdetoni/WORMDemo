package br.com.worm.demo;


import br.com.liviacare.worm.ActiveRecord;
import br.com.liviacare.worm.annotation.audit.Active;
import br.com.liviacare.worm.annotation.audit.CreatedAt;
import br.com.liviacare.worm.annotation.audit.DeletedAt;
import br.com.liviacare.worm.annotation.audit.UpdatedAt;
import br.com.liviacare.worm.annotation.mapping.*;
import br.com.liviacare.worm.api.Finder;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Book entity optimized for WORM best practices (Section 23, 27).
 * - Supports projection queries via BookProjection.find.* and BookDto.find.*
 * - Bulk batch operations via saveAllBatch, updateAllBatch, deleteAllBatch
 * - Query plan caching for repeated queries with same shape
 */
@DbTable("books")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Book extends ActiveRecord<Book, UUID>{
    public static final Finder<Book, UUID> find = ActiveRecord.find(Book.class);

    @DbId("id")
    private UUID id;

    @DbColumn("title")
    private String title;

    @DbColumn("isbn")
    private String isbn;

    @DbColumn("status")
    private String status;

    @DbColumn("author_id")
    private UUID authorId;

    @DbJoin(
        table = "authors",
        alias = "a",
        on = "a.id = a1.author_id",
        type = DbJoin.Type.LEFT
    )
    private Author author;

    @CreatedAt
    private LocalDateTime createdAt;

    @UpdatedAt
    private LocalDateTime updatedAt;

    @DeletedAt
    private LocalDateTime deletedAt;

    @Active
    private boolean active;

    @DbVersion
    private long version;
}