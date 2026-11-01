package com.example.footballanalysis.model.db;

import java.util.*;

import com.example.footballanalysis.model.db.user.Coach;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

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

    @ManyToMany(mappedBy = "teams")
    @Fetch(FetchMode.SUBSELECT)
    private Set<Coach> coaches = new HashSet<>();

    public Set<Coach> getCoaches() {
        return coaches;
    }

}
