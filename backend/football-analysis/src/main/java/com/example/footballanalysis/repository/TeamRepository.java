package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.Team;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.UUID;

@Repository
public interface TeamRepository extends JpaRepository<Team, UUID> {
    @Override
    @EntityGraph(attributePaths = {"club"})
    java.util.Optional<Team> findById(UUID id);

    @EntityGraph(attributePaths = {"coaches", "club"})
    @Query("select t from Team t where t.id = :id")
    java.util.Optional<Team> findByIdWithCoaches(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"club"})
    java.util.List<Team> findByClub_Id(UUID clubId);

    @Query("select t.id from Team t where t.club.id = :clubId")
    java.util.List<UUID> findIdsByClub_Id(@Param("clubId") UUID clubId);

    @Query("select t.id from Team t where t.club.id in :clubIds")
    java.util.List<UUID> findIdsByClub_IdIn(@Param("clubIds") Collection<UUID> clubIds);

    @Query("select t.id from Team t")
    java.util.List<UUID> findAllTeamIds();

    @Query("""
            select t from Team t
            left join fetch t.club club
            where (:pattern is null
                or lower(t.name) like :pattern escape '\\'
                or lower(coalesce(t.shortName, '')) like :pattern escape '\\'
                or lower(coalesce(club.name, '')) like :pattern escape '\\')
            order by lower(t.name) asc, t.id asc
            """)
    java.util.List<Team> findCatalog(@Param("pattern") String pattern, Pageable pageable);

    @Query("""
            select count(t) from Team t
            left join t.club club
            where (:pattern is null
                or lower(t.name) like :pattern escape '\\'
                or lower(coalesce(t.shortName, '')) like :pattern escape '\\'
                or lower(coalesce(club.name, '')) like :pattern escape '\\')
            """)
    long countCatalog(@Param("pattern") String pattern);

    @Query("""
            select t from Team t
            left join fetch t.club club
            where t.id in :ids
              and (:pattern is null
                or lower(t.name) like :pattern escape '\\'
                or lower(coalesce(t.shortName, '')) like :pattern escape '\\'
                or lower(coalesce(club.name, '')) like :pattern escape '\\')
            order by lower(t.name) asc, t.id asc
            """)
    java.util.List<Team> findCatalogByIds(
            @Param("ids") Collection<UUID> ids,
            @Param("pattern") String pattern,
            Pageable pageable);

    @Query("""
            select count(t) from Team t
            left join t.club club
            where t.id in :ids
              and (:pattern is null
                or lower(t.name) like :pattern escape '\\'
                or lower(coalesce(t.shortName, '')) like :pattern escape '\\'
                or lower(coalesce(club.name, '')) like :pattern escape '\\')
            """)
    long countCatalogByIds(@Param("ids") Collection<UUID> ids, @Param("pattern") String pattern);

    boolean existsByName(String name);
    boolean existsByNameAndIdNot(String name, UUID id);
}
