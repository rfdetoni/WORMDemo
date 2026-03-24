package br.com.worm.demo.dto;

import br.com.liviacare.worm.annotation.mapping.DbColumn;
import br.com.liviacare.worm.annotation.mapping.DbId;
import br.com.liviacare.worm.annotation.mapping.DbTable;
import br.com.liviacare.worm.api.Finder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Projection record mapped directly to books table.
 * Query via BookProjection.find.* (Finder style), for example:
 * BookProjection.find.all(FilterBuilder.create().eq("active", true)).
 */
@DbTable("books")
public record BookProjection(
    @DbId UUID id,
    @DbColumn("title") String title,
    @DbColumn("isbn") String isbn,
    @DbColumn("status") String status,
    @DbColumn("author_id") UUID authorId,
    @DbColumn("created_at") LocalDateTime createdAt,
    @DbColumn("updated_at") LocalDateTime updatedAt,
    @DbColumn("active") boolean active
) implements Finder<BookProjection, UUID> {

    @Override
    public Class<BookProjection> entityClass() {
        return BookProjection.class;
    }

    public static final Finder<BookProjection, UUID> find = () -> BookProjection.class;
}
