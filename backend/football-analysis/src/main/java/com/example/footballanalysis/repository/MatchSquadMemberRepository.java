package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.MatchSquadMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MatchSquadMemberRepository extends JpaRepository<MatchSquadMember, UUID> {

    void deleteAllByMatch_Id(UUID matchId);

    void deleteAllByTeam_Id(UUID teamId);
}