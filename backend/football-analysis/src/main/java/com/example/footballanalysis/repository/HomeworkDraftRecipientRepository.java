package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.HomeworkDraftRecipient;
import com.example.footballanalysis.model.db.HomeworkDraftRecipientId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface HomeworkDraftRecipientRepository extends JpaRepository<HomeworkDraftRecipient, HomeworkDraftRecipientId> {

    List<HomeworkDraftRecipient> findByHomework_Id(UUID homeworkId);

    List<HomeworkDraftRecipient> findByPlayerProfile_IdAndHomework_Team_Id(UUID playerProfileId, UUID teamId);
}
