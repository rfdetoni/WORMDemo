package br.com.worm.demo.mapper;

import br.com.liviacare.worm.util.UuidV7;
import br.com.worm.demo.Author;
import br.com.worm.demo.dto.AuthorDto;

import java.time.LocalDateTime;

public class AuthorMapper {

    public static AuthorDto toDto(Author a) {
        if (a == null) return null;
        return new AuthorDto(a.getId(), a.getName(), a.getEmail(), a.getCreatedAt(), a.getUpdatedAt());
    }

    public static Author fromCreate(String name, String email) {
        Author a = new Author();
        a.setId(UuidV7.next());
        a.setName(name);
        a.setEmail(email);
        a.setCreatedAt(LocalDateTime.now());
        a.setUpdatedAt(LocalDateTime.now());
        return a;
    }
}

