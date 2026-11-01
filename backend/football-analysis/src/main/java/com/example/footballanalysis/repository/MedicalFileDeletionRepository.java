package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.MedicalFileDeletion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MedicalFileDeletionRepository extends JpaRepository<MedicalFileDeletion, UUID> {
}
