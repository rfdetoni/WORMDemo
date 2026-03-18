package br.com.worm.demo.mapper;

import br.com.liviacare.worm.util.UuidV7;
import br.com.worm.demo.Book;

import java.time.LocalDateTime;

public class BookMapper {

    public static Book fromCreate(String title, String isbn, String status, java.util.UUID authorId) {
        Book b = new Book();
        b.setId(UuidV7.next());
        b.setTitle(title);
        b.setIsbn(isbn);
        b.setStatus(status);
        b.setAuthorId(authorId);
        b.setActive(true);
        b.setCreatedAt(LocalDateTime.now());
        b.setUpdatedAt(LocalDateTime.now());
        return b;
    }
}
