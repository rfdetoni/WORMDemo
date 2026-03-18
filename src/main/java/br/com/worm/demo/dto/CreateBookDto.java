package br.com.worm.demo.dto;

import java.util.UUID;

public record CreateBookDto(String title, String isbn, String status, UUID authorId) {}

