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

        Method page = clubMemberPage();
        String clubQuery = page.getAnnotation(Query.class).value();
        assertThat(clubQuery).contains("coach_teams");
        assertThat(clubQuery).contains("fan_favorite_teams");
        assertThat(clubQuery).contains("team_roster");
        assertThat(clubQuery).contains("player_parents");
        assertThat(clubQuery).doesNotContain("u.role");
        assertThat(page.getAnnotation(Query.class).nativeQuery()).isTrue();
    }

    /**
     * A klubtagok keresése csak névre és e-mailre szűr. A szerepkör-oszlop (u.role) csak az elsődleges szerepet
     * tárolja, ezért félrevezető találatot adna; a csapatnévre keresés pedig tagonként négy EXISTS volt.
     */
    @Test
    void clubMemberSearchMatchesOnlyNameAndEmail() throws Exception {
        String clubQuery = clubMemberPage().getAnnotation(Query.class).value();
        String countQuery = UserRepository.class.getMethod("countUsersInClub", UUID.class, String.class)
                .getAnnotation(Query.class).value();

        for (String query : new String[]{clubQuery, countQuery}) {
            assertThat(query).contains(UserSearchSql.NAME_OR_EMAIL_MATCH);
            assertThat(query).doesNotContain("u.role");
            assertThat(query).doesNotContain("team_match");
        }
        assertThat(clubQuery).contains("count(*) OVER ()");
    }

    /** A globális és a klubkeresés ugyanazt a feltételt használja, így mindkettő a V15 trigram indexeire illik. */
    @Test
    void globalSearchSharesTheIndexedCondition() throws Exception {
        Query global = UserRepository.class.getMethod(
                "findPageMatching", String.class, org.springframework.data.domain.Pageable.class)
                .getAnnotation(Query.class);
        assertThat(global.nativeQuery()).isTrue();
        assertThat(global.value()).contains(UserSearchSql.NAME_OR_EMAIL_MATCH);
        assertThat(global.countQuery()).contains(UserSearchSql.NAME_OR_EMAIL_MATCH);
    }

    private static Method clubMemberPage() throws NoSuchMethodException {
        return UserRepository.class.getMethod("findClubMemberPage", UUID.class, String.class, int.class, int.class);
    }
}
