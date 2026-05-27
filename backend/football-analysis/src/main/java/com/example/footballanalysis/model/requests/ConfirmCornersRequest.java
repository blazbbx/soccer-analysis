package com.example.footballanalysis.model.requests;

import com.example.footballanalysis.model.Corner;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ConfirmCornersRequest(
        @NotNull
        @Size(min = 4, max = 4, message = "Exactly four corners are required.")
        List<Corner> corners
) {}
