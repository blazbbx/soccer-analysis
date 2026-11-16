package com.example.footballanalysis.model.db;

import java.util.*;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "teams")
@Getter
@Setter
@NoArgsConstructor
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;
    private String shortName;
    private String logoUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id")
    private Club club;

    @Enumerated(EnumType.STRING)
    @Column(name = "medical_mode", nullable = false, length = 8)
    private MedicalMode medicalMode = MedicalMode.INHERIT;

    @Enumerated(EnumType.STRING)
    @Column(name = "medical_evidence", nullable = false, length = 16)
    private MedicalEvidence medicalEvidence = MedicalEvidence.INHERIT;

    // Coaches (coach_teams) and fans (fan_favorite_teams) are not mapped here: TeamMembershipQuery reads and writes them.

}
