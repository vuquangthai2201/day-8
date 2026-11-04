package com.example.library.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ReturnBookRequest(@NotBlank String penaltyPolicy) {}
