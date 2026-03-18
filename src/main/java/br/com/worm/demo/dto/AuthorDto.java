package br.com.worm.demo.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record AuthorDto(UUID id, String name, String email, LocalDateTime createdAt, LocalDateTime updatedAt) {}

