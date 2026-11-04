package com.example.library.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record BorrowBookRequest(@NotNull UUID memberId, @NotNull UUID bookId) {}
