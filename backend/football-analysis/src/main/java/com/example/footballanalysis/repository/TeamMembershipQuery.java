package com.example.footballanalysis.repository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class TeamMembershipQuery {

    private final EntityManager entityManager;

    public boolean isCoachOnTeam(UUID userId, UUID teamId) {
        if (userId == null || teamId == null) {
            return false;
        }
        return existsOnTeam("coach_teams", "coach_id", userId, teamId);
    }

    public boolean isCoachOnAnyTeam(UUID userId, Collection<UUID> teamIds) {
        return existsOnAnyTeam("coach_teams", "coach_id", userId, teamIds);
    }

    public boolean isFanOnTeam(UUID userId, UUID teamId) {
        if (userId == null || teamId == null) {
            return false;
        }
        return existsOnTeam("fan_favorite_teams", "fan_id", userId, teamId);
    }

    public boolean isFanOnAnyTeam(UUID userId, Collection<UUID> teamIds) {
        return existsOnAnyTeam("fan_favorite_teams", "fan_id", userId, teamIds);
    }

    public List<UUID> findCoachTeamIds(UUID userId) {
        return listTeamIds("coach_teams", "coach_id", userId);
    }

    public List<UUID> findFanTeamIds(UUID userId) {
        return listTeamIds("fan_favorite_teams", "fan_id", userId);
    }

    public List<UUID> findCoachIdsByTeamId(UUID teamId) {
        return listUserIds("coach_teams", "coach_id", teamId);
    }

    public Map<UUID, Long> countCoachesByTeamIds(Collection<UUID> teamIds) {
        if (teamIds == null || teamIds.isEmpty()) {
            return Map.of();
        }
        List<?> rows = entityManager.createNativeQuery(
                        "SELECT team_id, count(*) FROM coach_teams WHERE team_id IN (:teamIds) GROUP BY team_id")
                .setParameter("teamIds", teamIds)
                .getResultList();
        Map<UUID, Long> counts = new HashMap<>();
        for (Object row : rows) {
            Object[] cells = (Object[]) row;
            if (cells[0] == null || cells[1] == null) {
                continue;
            }
            counts.put(toUuid(cells[0]), ((Number) cells[1]).longValue());
        }
        return counts;
    }

    public List<UUID> findFanIdsByTeamId(UUID teamId) {
        return listUserIds("fan_favorite_teams", "fan_id", teamId);
    }

    public Map<UUID, List<UUID>> findCoachIdsByTeamIds(Collection<UUID> teamIds) {
        return listUserIdsByTeam("coach_teams", "coach_id", teamIds);
    }

    public Map<UUID, List<UUID>> findFanIdsByTeamIds(Collection<UUID> teamIds) {
        return listUserIdsByTeam("fan_favorite_teams", "fan_id", teamIds);
    }

    @Transactional
    public void insertCoachTeam(UUID userId, UUID teamId) {
        insert("coach_teams", "coach_id", userId, teamId);
    }

    @Transactional
    public void deleteCoachTeam(UUID userId, UUID teamId) {
        delete("coach_teams", "coach_id", userId, teamId);
    }

    @Transactional
    public void insertFanTeam(UUID userId, UUID teamId) {
        insert("fan_favorite_teams", "fan_id", userId, teamId);
    }

    @Transactional
    public void deleteFanTeam(UUID userId, UUID teamId) {
        delete("fan_favorite_teams", "fan_id", userId, teamId);
    }

    @Transactional
    public void deleteCoachTeamsForUser(UUID userId) {
        deleteAllForUser("coach_teams", "coach_id", userId);
    }

    @Transactional
    public void deleteFanTeamsForUser(UUID userId) {
        deleteAllForUser("fan_favorite_teams", "fan_id", userId);
    }

    @Transactional
    public void deleteCoachTeamsForTeam(UUID teamId) {
        deleteAllForTeam("coach_teams", teamId);
    }

    @Transactional
    public void deleteFanTeamsForTeam(UUID teamId) {
        deleteAllForTeam("fan_favorite_teams", teamId);
    }

    private boolean existsOnTeam(String table, String userColumn, UUID userId, UUID teamId) {
        return !entityManager.createNativeQuery(
                        "SELECT 1 FROM " + table + " WHERE " + userColumn + " = :userId AND team_id = :teamId LIMIT 1")
                .setParameter("userId", userId)
                .setParameter("teamId", teamId)
                .getResultList()
                .isEmpty();
    }

    private boolean existsOnAnyTeam(String table, String userColumn, UUID userId, Collection<UUID> teamIds) {
        if (userId == null || teamIds == null || teamIds.isEmpty()) {
            return false;
        }
        return !entityManager.createNativeQuery(
                        "SELECT 1 FROM " + table + " WHERE " + userColumn + " = :userId AND team_id IN (:teamIds) LIMIT 1")
                .setParameter("userId", userId)
                .setParameter("teamIds", teamIds)
                .getResultList()
                .isEmpty();
    }

    private List<UUID> listTeamIds(String table, String userColumn, UUID userId) {
        if (userId == null) {
            return List.of();
        }
        List<?> rows = entityManager.createNativeQuery(
                        "SELECT team_id FROM " + table + " WHERE " + userColumn + " = :userId")
                .setParameter("userId", userId)
                .getResultList();
        return rows.stream().map(TeamMembershipQuery::toUuid).toList();
    }

    private Map<UUID, List<UUID>> listUserIdsByTeam(String table, String userColumn, Collection<UUID> teamIds) {
        if (teamIds == null || teamIds.isEmpty()) {
            return Map.of();
        }
        List<?> rows = entityManager.createNativeQuery(
                        "SELECT team_id, " + userColumn + " FROM " + table + " WHERE team_id IN (:teamIds)")
                .setParameter("teamIds", teamIds)
                .getResultList();
        Map<UUID, List<UUID>> grouped = new HashMap<>();
        for (Object row : rows) {
            Object[] cells = (Object[]) row;
            if (cells[0] == null || cells[1] == null) {
                continue;
            }
            grouped.computeIfAbsent(toUuid(cells[0]), key -> new ArrayList<>()).add(toUuid(cells[1]));
        }
        return grouped;
    }

    private List<UUID> listUserIds(String table, String userColumn, UUID teamId) {
        if (teamId == null) {
            return List.of();
        }
        List<?> rows = entityManager.createNativeQuery(
                        "SELECT " + userColumn + " FROM " + table + " WHERE team_id = :teamId")
                .setParameter("teamId", teamId)
                .getResultList();
        return rows.stream().map(TeamMembershipQuery::toUuid).toList();
    }

    private void insert(String table, String userColumn, UUID userId, UUID teamId) {
        entityManager.createNativeQuery(
                        "INSERT INTO " + table + " (" + userColumn + ", team_id) VALUES (:userId, :teamId)")
                .setParameter("userId", userId)
                .setParameter("teamId", teamId)
                .executeUpdate();
    }

    private void delete(String table, String userColumn, UUID userId, UUID teamId) {
        entityManager.createNativeQuery(
                        "DELETE FROM " + table + " WHERE " + userColumn + " = :userId AND team_id = :teamId")
                .setParameter("userId", userId)
                .setParameter("teamId", teamId)
                .executeUpdate();
    }

    private void deleteAllForUser(String table, String userColumn, UUID userId) {
        if (userId == null) {
            return;
        }
        entityManager.createNativeQuery(
                        "DELETE FROM " + table + " WHERE " + userColumn + " = :userId")
                .setParameter("userId", userId)
                .executeUpdate();
    }

    private void deleteAllForTeam(String table, UUID teamId) {
        if (teamId == null) {
            return;
        }
        entityManager.createNativeQuery(
                        "DELETE FROM " + table + " WHERE team_id = :teamId")
                .setParameter("teamId", teamId)
                .executeUpdate();
    }

    private static UUID toUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        return UUID.fromString(value.toString());
    }
}
