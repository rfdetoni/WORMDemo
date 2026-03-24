package br.com.worm.demo.dto;

import br.com.liviacare.worm.annotation.mapping.DbColumn;
import br.com.liviacare.worm.annotation.mapping.DbId;
import br.com.liviacare.worm.annotation.mapping.DbTable;
import br.com.liviacare.worm.api.Finder;

import java.util.UUID;

/**
 * Lightweight author projection queried directly via Finder.
 */
@DbTable("authors")
public record AuthorSummary(
    @DbId UUID id,
    @DbColumn("name") String name,
    @DbColumn("email") String email
) implements Finder<AuthorSummary, UUID> {

    @Override
    public Class<AuthorSummary> entityClass() {
        return AuthorSummary.class;
    }

    public static final Finder<AuthorSummary, UUID> find = () -> AuthorSummary.class;
}
