package com.example.footballanalysis.model.requests;

import com.example.footballanalysis.model.db.HomeworkItemType;
import com.example.footballanalysis.model.db.HomeworkQuestionKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record HomeworkItemRequest(
        UUID id,                            // an existing item of this homework; null means a new one
        @Size(max = 64) String clientKey,   // temporary key of a new item, lives only within this request
        @NotNull HomeworkItemType type,
        @Size(max = 5000) String body,
        UUID clipId,
        @PositiveOrZero Integer focusMs,
        HomeworkQuestionKind questionKind,
        @Size(max = 6) List<@NotBlank @Size(max = 200) String> options,
        List<@PositiveOrZero Integer> correctOptions,
        Boolean required,                   // null means true, like the column default
        UUID refItemId,                     // TIMESTAMP question: an existing CLIP item
        @Size(max = 64) String refClientKey // or the clientKey of a new CLIP item in this request
) {}
