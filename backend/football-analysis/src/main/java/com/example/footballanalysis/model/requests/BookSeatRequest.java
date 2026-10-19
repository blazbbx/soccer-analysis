package com.example.footballanalysis.model.requests;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record BookSeatRequest(@NotNull UUID playerProfileId) {}
