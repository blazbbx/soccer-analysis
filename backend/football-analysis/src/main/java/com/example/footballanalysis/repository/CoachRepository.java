package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.user.Coach;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CoachRepository extends JpaRepository<Coach, UUID> {

}

