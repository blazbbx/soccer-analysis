package com.example.footballanalysis.model.responses;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Where to read a club's privacy notice. {@code kind} URL: the club's own https link. {@code kind} FILE: a
 * short-lived presigned link to the uploaded PDF, with {@code expiresInSeconds}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClubMedicalNoticeResponse(String kind, String url, Long expiresInSeconds) {}
