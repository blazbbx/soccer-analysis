package com.example.footballanalysis.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RoutePermissionsConfigTest {

    @Test
    void clubWriteRoutesAreAdminAndClubAdminOnly() throws Exception {
        List<Map<String, Object>> routes = routes();

        assertThat(roles(routes, "POST", "/api/clubs")).containsExactly("admin");
        assertThat(roles(routes, "PUT", "/api/clubs/*")).containsExactly("admin", "club_admin");
        assertThat(roles(routes, "DELETE", "/api/clubs/*")).containsExactly("admin", "club_admin");
        assertThat(roles(routes, "POST", "/api/clubs/*/teams")).containsExactly("admin", "club_admin");
        assertThat(roles(routes, "POST", "/api/clubs/*/admin-invites")).containsExactly("admin", "club_admin");
        assertThat(roles(routes, "GET", "/api/clubs/*/users")).containsExactly("admin", "club_admin");
        assertThat(roles(routes, "PUT", "/api/users/*")).containsExactly("admin", "club_admin");
        assertThat(roles(routes, "PUT", "/api/users/me")).contains("club_admin");
        assertThat(roles(routes, "GET", "/api/clubs/*/admins")).containsExactly("admin", "club_admin");
        assertThat(roles(routes, "DELETE", "/api/clubs/*/admins/*")).containsExactly("admin", "club_admin");
        assertThat(roles(routes, "GET", "/api/clubs")).contains("club_admin");
        assertThat(roles(routes, "PUT", "/api/teams/*")).contains("club_admin");
        assertThat(roles(routes, "DELETE", "/api/teams/*")).contains("club_admin");
        assertThat(roles(routes, "POST", "/api/teams/*/player-profiles")).containsExactly("admin", "club_admin");
        assertThat(roles(routes, "POST", "/api/teams/*/players/*")).contains("club_admin");
        assertThat(roles(routes, "DELETE", "/api/teams/*/players/*")).contains("club_admin");
        assertThat(roles(routes, "DELETE", "/api/teams/*/parents/*")).containsExactly("admin", "club_admin");
        assertThat(roles(routes, "GET", "/api/player-profiles/*/parents")).containsExactly("admin", "club_admin");
        assertThat(roles(routes, "DELETE", "/api/player-profiles/*/parents/*")).containsExactly("admin", "club_admin");
        assertThat(roles(routes, "POST", "/api/player-profiles/*/parent-invites")).contains("club_admin");
        assertThat(roles(routes, "GET", "/api/player-profiles/*/parent-invites"))
                .containsExactly("admin", "club_admin", "coach", "player", "parent", "fan");
        assertThat(roles(routes, "DELETE", "/api/player-profiles/*/parent-invites/*"))
                .containsExactly("admin", "club_admin", "coach", "player", "parent", "fan");
        assertThat(roles(routes, "GET", "/api/teams/*")).contains("club_admin");
        assertThat(roles(routes, "GET", "/api/me/views"))
                .containsExactly("admin", "club_admin", "coach", "player", "parent", "fan");
        assertThat(roles(routes, "GET", "/api/me/children")).contains("club_admin");
        assertThat(roles(routes, "GET", "/api/matches"))
                .containsExactly("admin", "club_admin", "coach", "player", "parent", "fan");
        assertThat(roles(routes, "GET", "/api/matches/*"))
                .containsExactly("admin", "club_admin", "coach", "player", "parent", "fan");
        assertThat(roles(routes, "GET", "/api/matches/*/clips"))
                .containsExactly("admin", "club_admin", "coach", "player", "parent", "fan");
        assertThat(roles(routes, "GET", "/api/clips/*"))
                .containsExactly("admin", "club_admin", "coach", "player", "parent");
        assertThat(roles(routes, "GET", "/api/clips/*/access"))
                .containsExactly("admin", "club_admin", "coach", "player", "parent", "fan");
        assertThat(roles(routes, "POST", "/api/clips")).doesNotContain("club_admin");
        assertThat(roles(routes, "POST", "/api/clips/upload-links")).doesNotContain("club_admin");
        assertThat(roles(routes, "POST", "/api/clips/*/composition/complete")).doesNotContain("club_admin");
        assertThat(roles(routes, "PUT", "/api/clips/*")).doesNotContain("club_admin");
        assertThat(roles(routes, "DELETE", "/api/clips/*")).doesNotContain("club_admin");
        assertThat(roles(routes, "POST", "/api/matches/upload")).doesNotContain("club_admin");
        assertThat(roles(routes, "PUT", "/api/matches/*")).doesNotContain("club_admin");
        assertThat(roles(routes, "DELETE", "/api/matches/*")).doesNotContain("club_admin");
        assertThat(roles(routes, "GET", "/api/cups/")).doesNotContain("club_admin");
        assertThat(routes.stream().map(route -> String.valueOf(route.get("path"))))
                .doesNotContain("/api/teams/*/member-candidates");
        assertThat(roles(routes, "GET", "/api/teams/*/chat")).contains("club_admin");
        assertThat(roles(routes, "POST", "/api/teams/*/chat")).contains("club_admin");
        assertThat(roles(routes, "GET", "/api/teams/*/chat/subscribe")).contains("club_admin");
        assertThat(routes.stream()
                .filter(route -> "/api/teams/*/coaches/*".equals(route.get("path")))
                .map(RoutePermissionsConfigTest::rolesOf)
                .findFirst()
                .orElseThrow()).contains("club_admin");
        assertThat(routes.stream()
                .filter(route -> "/api/teams/*/fans/*".equals(route.get("path")))
                .map(RoutePermissionsConfigTest::rolesOf)
                .findFirst()
                .orElseThrow()).contains("club_admin");
        assertThat(routes.stream()
                .filter(route -> "/api/team-invites/*/invites".equals(route.get("path")))
                .map(RoutePermissionsConfigTest::rolesOf)
                .findFirst()
                .orElseThrow()).contains("club_admin");
        assertThat(routes.stream()
                .filter(route -> "/api/team-invites/*/accept".equals(route.get("path")))
                .map(RoutePermissionsConfigTest::rolesOf)
                .findFirst()
                .orElseThrow()).contains("club_admin");
        assertThat(roles(routes, "GET", "/api/teams/*/events"))
                .containsExactly("admin", "club_admin", "coach", "player", "parent");
        assertThat(roles(routes, "GET", "/api/teams/*/events/*"))
                .containsExactly("admin", "club_admin", "coach", "player", "parent");
        assertThat(roles(routes, "POST", "/api/teams/*/events"))
                .containsExactly("admin", "club_admin", "coach");
        assertThat(roles(routes, "PUT", "/api/teams/*/events/*"))
                .containsExactly("admin", "club_admin", "coach");
        assertThat(roles(routes, "DELETE", "/api/teams/*/events/*"))
                .containsExactly("admin", "club_admin", "coach");
        assertThat(roles(routes, "PUT", "/api/teams/*/events/*/attendance/*"))
                .containsExactly("admin", "club_admin", "player", "parent");
        assertThat(roles(routes, "POST", "/api/teams/*/events/*/rides"))
                .containsExactly("club_admin", "coach", "parent", "player");
        assertThat(roles(routes, "PUT", "/api/teams/*/events/*/rides/*"))
                .containsExactly("club_admin", "coach", "parent", "player");
        assertThat(roles(routes, "DELETE", "/api/teams/*/events/*/rides/*"))
                .containsExactly("club_admin", "coach", "parent", "player");
        assertThat(roles(routes, "POST", "/api/teams/*/events/*/rides/*/seats"))
                .containsExactly("admin", "club_admin", "player", "parent");
        assertThat(roles(routes, "DELETE", "/api/teams/*/events/*/rides/*/seats/*"))
                .containsExactly("admin", "club_admin", "player", "parent");
        assertThat(roles(routes, "PUT", "/api/player-profiles/*/ride-offer-right"))
                .containsExactly("parent");

        for (String path : List.of(
                "/api/clubs",
                "/api/clubs/*",
                "/api/clubs/*/teams",
                "/api/clubs/*/admin-invites",
                "/api/clubs/*/admins",
                "/api/clubs/*/admins/*")) {
            for (Map<String, Object> route : routes) {
                if (!path.equals(route.get("path"))) {
                    continue;
                }
                String method = String.valueOf(route.get("method"));
                if ("GET".equals(method)) {
                    continue;
                }
                assertThat(rolesOf(route)).doesNotContain("coach", "parent", "fan", "player");
            }
        }
    }

    @Test
    void sportMedicalRoutesMatchThePlan() throws Exception {
        List<Map<String, Object>> routes = routes();

        assertThat(roles(routes, "POST", "/api/player-profiles/*/medical-certificates"))
                .containsExactly("admin", "club_admin", "coach", "parent", "player");
        assertThat(roles(routes, "POST", "/api/player-profiles/*/medical-certificates/*/complete"))
                .containsExactly("admin", "club_admin", "coach", "parent", "player");
        // The coach opens the file only where its club allows it; the service checks that.
        assertThat(roles(routes, "GET", "/api/player-profiles/*/medical-certificates/*/file"))
                .containsExactly("admin", "club_admin", "coach", "parent", "player");
        assertThat(roles(routes, "POST", "/api/player-profiles/*/medical/verification")).containsExactly("club_admin", "coach");
        assertThat(roles(routes, "DELETE", "/api/player-profiles/*/medical/verification")).containsExactly("club_admin", "coach");
        assertThat(roles(routes, "POST", "/api/player-profiles/*/medical/rejection")).containsExactly("club_admin", "coach");
        assertThat(roles(routes, "POST", "/api/player-profiles/*/medical/date-correction")).containsExactly("club_admin", "coach");
        assertThat(roles(routes, "POST", "/api/player-profiles/*/medical/in-person")).containsExactly("club_admin", "coach");
        assertThat(roles(routes, "GET", "/api/clubs/*/medical-status")).containsExactly("club_admin");
        assertThat(roles(routes, "POST", "/api/player-profiles/*/medical/seen")).containsExactly("parent");
        assertThat(roles(routes, "PUT", "/api/me/medical/parent-access")).containsExactly("player");
        assertThat(roles(routes, "DELETE", "/api/player-profiles/*/medical-certificates/*"))
                .containsExactly("admin", "club_admin", "parent", "player");
        // The platform admin has no medical view: it reads only through admin-access, with a reason.
        assertThat(roles(routes, "GET", "/api/player-profiles/*/medical"))
                .containsExactly("club_admin", "coach", "parent", "player");
        assertThat(roles(routes, "POST", "/api/player-profiles/*/medical/admin-access")).containsExactly("admin");
        assertThat(roles(routes, "POST", "/api/player-profiles/*/medical/admin-withdrawal")).containsExactly("admin");
        assertThat(roles(routes, "PUT", "/api/player-profiles/*/medical-upload-right"))
                .containsExactly("admin", "club_admin", "parent");
        assertThat(roles(routes, "GET", "/api/me/medical")).containsExactly("player");
        assertThat(roles(routes, "GET", "/api/teams/*/medical-status")).containsExactly("club_admin", "coach");
        // Only the club's own admins write the club's settings; the platform admin still reads them for support.
        assertThat(roles(routes, "PUT", "/api/teams/*/medical-mode")).containsExactly("club_admin");
        assertThat(roles(routes, "GET", "/api/clubs/*/medical-settings")).containsExactly("admin", "club_admin");
        assertThat(roles(routes, "PUT", "/api/clubs/*/medical-settings")).containsExactly("club_admin");
        assertThat(roles(routes, "GET", "/api/clubs/*/medical-admin-accesses")).containsExactly("club_admin");
        assertThat(roles(routes, "PUT", "/api/clubs/*/medical-notice")).containsExactly("club_admin");
        assertThat(roles(routes, "DELETE", "/api/clubs/*/medical-notice")).containsExactly("club_admin");
        assertThat(roles(routes, "GET", "/api/clubs/*/medical-notice"))
                .containsExactly("admin", "club_admin", "coach", "player", "parent", "fan");

        // The longer certificate paths come before the shorter one, so complete and file are not caught by it.
        List<String> paths = routes.stream().map(route -> String.valueOf(route.get("path"))).toList();
        assertThat(paths.indexOf("/api/player-profiles/*/medical-certificates/*/complete"))
                .isLessThan(paths.indexOf("/api/player-profiles/*/medical-certificates"));
        assertThat(paths.indexOf("/api/player-profiles/*/medical/admin-access"))
                .isLessThan(paths.indexOf("/api/player-profiles/*/medical"));
        // Only the privacy notice is open to every signed-in role, fans too: it is not secret.
        assertThat(routes.stream().filter(route -> String.valueOf(route.get("path")).contains("medical"))
                .filter(route -> !String.valueOf(route.get("path")).endsWith("/medical-notice")))
                .allSatisfy(route -> assertThat(rolesOf(route)).doesNotContain("fan"));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> routes() throws Exception {
        try (InputStream in = RoutePermissionsConfigTest.class.getResourceAsStream("/application.yml")) {
            Object first = new Yaml().loadAll(in).iterator().next();
            Map<String, Object> root = (Map<String, Object>) first;
            Map<String, Object> idp = (Map<String, Object>) root.get("idp");
            return (List<Map<String, Object>>) idp.get("route-permissions");
        }
    }

    private static List<String> roles(List<Map<String, Object>> routes, String method, String path) {
        return routes.stream()
                .filter(route -> path.equals(route.get("path")) && method.equals(String.valueOf(route.get("method"))))
                .map(RoutePermissionsConfigTest::rolesOf)
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static List<String> rolesOf(Map<String, Object> route) {
        return (List<String>) route.get("roles");
    }
}
