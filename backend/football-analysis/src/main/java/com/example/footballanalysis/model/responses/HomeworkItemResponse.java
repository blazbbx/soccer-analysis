package com.example.footballanalysis.model.responses;

import com.example.footballanalysis.model.db.HomeworkItemType;
import com.example.footballanalysis.model.db.HomeworkQuestionKind;

import java.util.List;
import java.util.UUID;

/** The coach's view of an item, with the correct options. */
public record HomeworkItemResponse(
        UUID id,
        int position,
        HomeworkItemType type,
        String body,
        UUID clipId,
        String clipName,
        String clipRenderStatus,
        Integer focusMs,
        HomeworkQuestionKind questionKind,
        List<String> options,
        List<Integer> correctOptions,
        boolean required,
        UUID refItemId
) {}
