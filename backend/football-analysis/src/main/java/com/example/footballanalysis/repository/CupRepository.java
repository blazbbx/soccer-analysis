package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.Cup;
import com.example.footballanalysis.model.responses.CupListItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CupRepository extends JpaRepository<Cup, UUID> {

    List<Cup> findByCreatedBy_IdOrderByCreatedAtDesc(UUID createdById);

    List<Cup> findDistinctByCreatedBy_IdOrTeams_RealTeam_IdIn(UUID createdById, Collection<UUID> teamIds);

    boolean existsByIdAndCreatedBy_Id(UUID cupId, UUID createdById);

    Optional<Cup> findById(UUID id);

    @Query("""
            select new com.example.footballanalysis.model.responses.CupListItem(
                cup.id,
                cup.name,
                cup.createdAt,
                cup.createdBy.id,
                cup.createdBy.firstName,
                cup.createdBy.lastName,
                (select count(team.id) from CupTeam team where team.cup = cup),
                (select count(match.id) from CupMatch match where match.cup = cup)
            )
            from Cup cup
            order by cup.createdAt desc, cup.id desc
            """)
    List<CupListItem> findListItems(Pageable pageable);

    @Query("select count(cup) from Cup cup")
    long countListItems();

    @Query("""
            select new com.example.footballanalysis.model.responses.CupListItem(
                cup.id,
                cup.name,
                cup.createdAt,
                cup.createdBy.id,
                cup.createdBy.firstName,
                cup.createdBy.lastName,
                (select count(team.id) from CupTeam team where team.cup = cup),
                (select count(match.id) from CupMatch match where match.cup = cup)
            )
            from Cup cup
            where cup.createdBy.id = :actorId
            order by cup.createdAt desc, cup.id desc
            """)
    List<CupListItem> findListItemsCreatedBy(@Param("actorId") UUID actorId, Pageable pageable);

    @Query("select count(cup) from Cup cup where cup.createdBy.id = :actorId")
    long countListItemsCreatedBy(@Param("actorId") UUID actorId);

    @Query("""
            select new com.example.footballanalysis.model.responses.CupListItem(
                cup.id,
                cup.name,
                cup.createdAt,
                cup.createdBy.id,
                cup.createdBy.firstName,
                cup.createdBy.lastName,
                (select count(team.id) from CupTeam team where team.cup = cup),
                (select count(match.id) from CupMatch match where match.cup = cup)
            )
            from Cup cup
            where cup.createdBy.id = :actorId
               or exists (
                    select 1 from CupTeam team
                    where team.cup = cup and team.realTeam.id in :teamIds
               )
            order by cup.createdAt desc, cup.id desc
            """)
    List<CupListItem> findListItemsForTeams(
            @Param("actorId") UUID actorId,
            @Param("teamIds") Collection<UUID> teamIds,
            Pageable pageable);

    @Query("""
            select count(cup) from Cup cup
            where cup.createdBy.id = :actorId
               or exists (
                    select 1 from CupTeam team
                    where team.cup = cup and team.realTeam.id in :teamIds
               )
            """)
    long countListItemsForTeams(@Param("actorId") UUID actorId, @Param("teamIds") Collection<UUID> teamIds);

    @Query("""
            select new com.example.footballanalysis.model.responses.CupListItem(
                cup.id,
                cup.name,
                cup.createdAt,
                cup.createdBy.id,
                cup.createdBy.firstName,
                cup.createdBy.lastName,
                (select count(team.id) from CupTeam team where team.cup = cup),
                (select count(match.id) from CupMatch match where match.cup = cup)
            )
            from Cup cup
            where exists (
                select 1 from CupTeam team
                where team.cup = cup and team.realTeam.id in :teamIds
            )
            order by cup.createdAt desc, cup.id desc
            """)
    List<CupListItem> findListItemsContainingTeams(
            @Param("teamIds") Collection<UUID> teamIds,
            Pageable pageable);

    @Query("""
            select count(cup) from Cup cup
            where exists (
                select 1 from CupTeam team
                where team.cup = cup and team.realTeam.id in :teamIds
            )
            """)
    long countListItemsContainingTeams(@Param("teamIds") Collection<UUID> teamIds);
}
