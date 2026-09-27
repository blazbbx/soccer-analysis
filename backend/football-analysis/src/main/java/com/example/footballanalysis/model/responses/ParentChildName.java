package com.example.footballanalysis.model.responses;

import java.util.UUID;

public record ParentChildName(UUID parentId, String firstName, String lastName) {
}
