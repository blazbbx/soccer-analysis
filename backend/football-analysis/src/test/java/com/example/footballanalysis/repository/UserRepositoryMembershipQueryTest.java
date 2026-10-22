package com.example.footballanalysis.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserRepositoryMembershipQueryTest {

    @Test
    void parentAndClubQueriesUseMembershipTablesNotTheRoleStamp() throws Exception {
        Method parents = UserRepository.class.getMethod("findParentsOnTeam", UUID.class);
        String parentQuery = parents.getAnnotation(Query.class).value();
        assertThat(parentQuery).doesNotContain("UserRole.PARENT");
        assertThat(parentQuery).contains("PlayerParent");
        assertThat(parentQuery).contains("TeamRoster");

        Method ids = UserRepository.class.getMethod(
                "findUserIdsInClubPage", UUID.class, String.class, String.class, int.class, int.class);
        String clubQuery = ids.getAnnotation(Query.class).value();
        assertThat(clubQuery).contains("coach_teams");
        assertThat(clubQuery).contains("fan_favorite_teams");
        assertThat(clubQuery).contains("team_roster");
        assertThat(clubQuery).contains("player_parents");
        assertThat(clubQuery).doesNotContain("user_type");
        assertThat(ids.getAnnotation(Query.class).nativeQuery()).isTrue();
    }
}
