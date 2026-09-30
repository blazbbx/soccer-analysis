package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);

    long countByRole(UserRole role);

    @Query("""
            select u from User u
            where u.role = com.example.footballanalysis.model.db.user.UserRole.PARENT
              and u.id in (
                select link.user.id from PlayerParent link
                where link.playerProfile.id in (
                    select roster.playerProfile.id from TeamRoster roster where roster.team.id = :teamId
                )
              )
            order by u.lastName, u.firstName
            """)
    List<User> findParentsOnTeam(@Param("teamId") UUID teamId);

    @Query("""
            select distinct u from User u
            where u.id in (select membership.user.id from ClubAdminMembership membership where membership.club.id = :clubId)
               or u.id in (select coach.id from Coach coach join coach.teams team where team.club.id = :clubId)
               or u.id in (select fan.id from Fan fan join fan.teams team where team.club.id = :clubId)
               or u.id in (
                    select profile.linkedUser.id from TeamRoster roster
                    join roster.playerProfile profile
                    where roster.team.club.id = :clubId and profile.linkedUser is not null
               )
               or u.id in (
                    select link.user.id from PlayerParent link
                    where link.playerProfile.id in (
                        select roster.playerProfile.id from TeamRoster roster where roster.team.club.id = :clubId
                    )
               )
            order by u.lastName, u.firstName
            """)
    List<User> findUsersInClub(@Param("clubId") UUID clubId);
}
