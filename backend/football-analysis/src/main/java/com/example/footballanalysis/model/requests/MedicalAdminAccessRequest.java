package com.example.footballanalysis.model.requests;

/** A platform admin's look at the technical facts. The reason is 20 to 500 characters and is shown to the club. */
public record MedicalAdminAccessRequest(String reason) {
}
