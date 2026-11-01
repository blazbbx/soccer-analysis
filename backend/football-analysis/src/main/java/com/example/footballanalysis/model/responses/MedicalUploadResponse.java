package com.example.footballanalysis.model.responses;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/**
 * {@code completed: true}: a date-only submission, already current; there is nothing to upload. Otherwise the
 * browser puts the file on {@code uploadUrl} and calls complete.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MedicalUploadResponse(UUID certificateId, String uploadUrl, Long expiresInSeconds, boolean completed) {}
