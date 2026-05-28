package com.example.footballanalysis.model.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatMessageRequest(
        @NotBlank(message = "{validation.message.content.required}")
        @Size(max = 5000, message = "{validation.message.content.max}")
        String content
) {
}
