package br.com.worm.demo.dto;

import br.com.liviacare.worm.annotation.mapping.DbColumn;
import br.com.liviacare.worm.annotation.mapping.DbId;
import br.com.liviacare.worm.annotation.mapping.DbJoin;
import br.com.liviacare.worm.annotation.mapping.DbTable;
import br.com.liviacare.worm.api.Finder;

import java.time.LocalDateTime;
import java.util.UUID;

@DbTable("books")
public record BookDto(
		@DbId UUID id,
		@DbColumn("title") String title,
		@DbColumn("isbn") String isbn,
		@DbColumn("status") String status,
		@DbColumn("author_id") UUID authorId,
		@DbJoin(table = "authors", alias = "author", on = "author.id = bookDto.author_id")
		AuthorRef author,

		@DbColumn(expr = "author.name", value = "authorName") String authorName,
		@DbColumn("created_at") LocalDateTime createdAt,
		@DbColumn("updated_at") LocalDateTime updatedAt,
		@DbColumn("deleted_at") LocalDateTime deletedAt,
		@DbColumn("active") boolean active
) implements Finder<BookDto, UUID> {

	public record AuthorRef(
			@DbId UUID id
	) {}

	@Override
	public Class<BookDto> entityClass() {
		return BookDto.class;
	}

	public static final Finder<BookDto, UUID> find = () -> BookDto.class;
}
