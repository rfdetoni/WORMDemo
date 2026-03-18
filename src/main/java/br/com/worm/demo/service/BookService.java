package br.com.worm.demo.service;

import br.com.liviacare.worm.query.FilterBuilder;
import br.com.liviacare.worm.query.Pageable;
import br.com.liviacare.worm.query.Slice;
import br.com.worm.demo.Book;
import br.com.worm.demo.dto.BookDto;
import br.com.worm.demo.mapper.BookMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class BookService {

    public BookDto findById(UUID id) {
        return BookDto.find.byId(id).orElse(null);
    }

    public List<BookDto> findAll() {
        return BookDto.find.all();
    }

    public List<BookDto> findAllByFilter(FilterBuilder filter) {
        return BookDto.find.all(filter);
    }

    public Slice<BookDto> findAllPaged(FilterBuilder filter, Pageable pageable) {
        return BookDto.find.all(filter, pageable);
    }

    @Transactional
    public Book save(Book book) {
        book.save();
        return book;
    }

    @Transactional
    public void create(String title, String isbn, String status, java.util.UUID authorId) {
        Book b = BookMapper.fromCreate(title, isbn, status == null ? "DRAFT" : status, authorId);
        b.save();
    }

    @Transactional
    public void deleteById(UUID id) {
        Book.find.byId(id).ifPresent(Book::delete);
    }
}

