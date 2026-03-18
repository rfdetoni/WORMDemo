package br.com.worm.demo;

import br.com.liviacare.worm.ActiveRecord;
import br.com.liviacare.worm.annotation.audit.CreatedAt;
import br.com.liviacare.worm.annotation.audit.UpdatedAt;
import br.com.liviacare.worm.annotation.mapping.DbColumn;
import br.com.liviacare.worm.annotation.mapping.DbId;
import br.com.liviacare.worm.annotation.mapping.DbTable;
import br.com.liviacare.worm.annotation.mapping.DbVersion;
import br.com.liviacare.worm.api.Finder;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@DbTable("authors")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Author extends ActiveRecord<Author, UUID> {

    public static final Finder<Author, UUID> find = ActiveRecord.find(Author.class);

    @DbId("id")
    private UUID id;

    @DbColumn("name")
    private String name;

    @DbColumn("email")
    private String email;

    @CreatedAt
    private LocalDateTime createdAt;

    @UpdatedAt
    private LocalDateTime updatedAt;

    @DbVersion
    private long version;
}