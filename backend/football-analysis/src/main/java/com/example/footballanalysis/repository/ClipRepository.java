package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.Clip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ClipRepository extends JpaRepository<Clip, UUID> {

	List<Clip> findAllByMatch_IdIn(Collection<UUID> matchIds);

	void deleteAllByMatch_IdIn(Collection<UUID> matchIds);

}
