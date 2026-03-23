package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.exception.UnauthorizedException;
import com.example.footballanalysis.model.db.Clip;
import com.example.footballanalysis.model.db.Match;
import com.example.footballanalysis.model.db.user.Coach;
import com.example.footballanalysis.model.db.user.Fan;
import com.example.footballanalysis.model.db.user.Player;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.db.Team;
import com.example.footballanalysis.model.requests.CreateTeamRequest;
import com.example.footballanalysis.model.requests.UpdateTeamRequest;
import com.example.footballanalysis.model.responses.TeamResponse;
import com.example.footballanalysis.repository.ClipRepository;
import com.example.footballanalysis.repository.CoachRepository;
import com.example.footballanalysis.repository.FanRepository;
import com.example.footballanalysis.repository.MatchRepository;
import com.example.footballanalysis.repository.MatchSquadMemberRepository;
import com.example.footballanalysis.repository.PlayerRepository;
import com.example.footballanalysis.repository.TeamInviteRepository;
import com.example.footballanalysis.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final CoachRepository coachRepository;
    private final FanRepository fanRepository;
    private final TeamInviteRepository teamInviteRepository;
    private final MatchRepository matchRepository;
    private final ClipRepository clipRepository;
    private final MatchSquadMemberRepository matchSquadMemberRepository;
    private final MinioObjectCleanupService minioObjectCleanupService;

    /**
     * Lekérdezi az összes csapatot.
     *
     * @return a csapatok listája (List<TeamResponse>)
     */
    @Transactional(readOnly = true)
    public List<TeamResponse> getAllTeams() {
        return teamRepository.findAll().stream().map(this::toResponse).toList();
    }

    /**
     * Lekérdezi azokat a csapatokat, amelyekhez a bejelentkezett felhasználó tartozik.
     * Adminisztrátor esetén az összes csapatot visszaadja.
     *
     * @param authentication a bejelentkezett felhasználó autentikációs objektuma
     * @return a felhasználóhoz tartozó csapatok listája (List<TeamResponse>)
     */
    @Transactional(readOnly = true)
    public List<TeamResponse> getMyTeams(Authentication authentication) {
        JwtAuthenticationToken jwtAuthentication = requireJwtAuthentication(authentication);
        Jwt jwt = jwtAuthentication.getToken();
        UserRole role = resolveRole(authentication);

        List<Team> teams = switch (role) {
            case ADMIN -> teamRepository.findAll();
            case COACH -> resolveCoach(jwt).getTeams().stream().toList();
            case PLAYER -> resolvePlayer(jwt).getTeams().stream().toList();
            case FAN -> resolveFan(jwt).getTeams().stream().toList();
        };

        return teams.stream().map(this::toResponse).toList();
    }

    /**
     * Lekérdez egy konkrét csapatot egyedi azonosító alapján.
     *
     * @param id a csapat egyedi azonosítója (UUID)
     * @return a csapat adatainak válasz objektuma (TeamResponse)
     */
    @Transactional(readOnly = true)
    public TeamResponse getTeam(UUID id) {
        return toResponse(teamRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("error.team.not_found", new Object[]{id}, "Team not found: " + id)));
    }

    /**
     * Létrehoz egy új csapatot.
     *
     * @param request az új csapat adatait tartalmazó kérés (CreateTeamRequest)
     * @return a létrehozott csapat válasz objektuma (TeamResponse)
     */
    @Transactional
    public TeamResponse createTeam(CreateTeamRequest request) {
        return createTeam(request, null);
    }

    /**
     * Létrehoz egy új csapatot, és ha a JWT le van kérve, akkor az edzőhöz is hozzárendeli.
     *
     * @param request az új csapat adatait tartalmazó kérés (CreateTeamRequest)
     * @param jwt a hitelesített felhasználó JWT tokenje
     * @return a létrehozott csapat válasz objektuma (TeamResponse)
     */
    @Transactional
    public TeamResponse createTeam(CreateTeamRequest request, Jwt jwt) {
        String name = request.name().trim();
        if (name.isBlank()) {
            throw new BadRequestException("validation.team.name.required", new Object[0], "Team name is required.");
        }
        if (teamRepository.existsByName(name)) {
            throw new ConflictException("error.team.name.conflict", new Object[]{name}, "A team with this name already exists: " + name);
        }

        Team team = new Team();
        team.setName(name);
        team.setShortName(request.shortName());
        team.setLogoUrl(request.logoUrl());

        Team savedTeam = teamRepository.save(team);
        if (jwt != null) {
            Coach coach = resolveCoach(jwt);
            coach.addTeam(savedTeam);
            coachRepository.save(coach);
        }

        return toResponse(savedTeam);
    }

    /**
     * Frissíti a meglévő csapat adatait.
     *
     * @param id a frissítendő csapat egyedi azonosítója (UUID)
     * @param request a frissített csapat adatait tartalmazó kérés (UpdateTeamRequest)
     * @return a frissített csapat válasz objektuma (TeamResponse)
     */
    @Transactional
    public TeamResponse updateTeam(UUID id, UpdateTeamRequest request) {
        Team team = teamRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("error.team.not_found", new Object[]{id}, "Team not found: " + id));

        String name = request.name().trim();
        if (name.isBlank()) {
            throw new BadRequestException("validation.team.name.required", new Object[0], "Team name is required.");
        }
        if (teamRepository.existsByNameAndIdNot(name, id)) {
            throw new ConflictException("error.team.name.conflict", new Object[]{name}, "Another team with this name already exists: " + name);
        }

        team.setName(name);
        team.setShortName(request.shortName());
        team.setLogoUrl(request.logoUrl());
        return toResponse(teamRepository.save(team));
    }

    /**
     * Törli a csapatot a megadott azonosító alapján.
     *
     * @param id a törlendő csapat egyedi azonosítója (UUID)
     */
    @Transactional
    public void deleteTeam(UUID id) {
        Team team = teamRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("error.team.not_found", new Object[]{id}, "Team not found: " + id));

        List<Match> matches = matchRepository.findAllByHomeTeam_IdOrAwayTeam_Id(id, id);
        List<UUID> matchIds = matches.stream().map(Match::getId).toList();
        List<Clip> clips = matchIds.isEmpty()
                ? List.of()
                : clipRepository.findAllByMatch_IdIn(matchIds);

        clearTeamMemberships(team);
        teamInviteRepository.deleteAllByTeam_Id(id);
        matchSquadMemberRepository.deleteAllByTeam_Id(id);

        if (!matchIds.isEmpty()) {
            clipRepository.deleteAllByMatch_IdIn(matchIds);
            matchRepository.deleteAll(matches);
        }

        teamRepository.delete(team);
        teamRepository.flush();

        minioObjectCleanupService.deleteMatchArtifactsForTeamDeletion(matches, clips);
    }

    private void clearTeamMemberships(Team team) {
        List<Player> players = team.getPlayers().stream().toList();
        for (Player player : players) {
            player.removeTeam(team);
        }
        if (!players.isEmpty()) {
            playerRepository.saveAll(players);
        }

        List<Coach> coaches = team.getCoaches().stream().toList();
        for (Coach coach : coaches) {
            coach.removeTeam(team);
        }
        if (!coaches.isEmpty()) {
            coachRepository.saveAll(coaches);
        }

        List<Fan> fans = fanRepository.findAllByTeams_Id(team.getId());
        for (Fan fan : fans) {
            fan.removeTeam(team);
        }
        if (!fans.isEmpty()) {
            fanRepository.saveAll(fans);
        }
    }

    // ── Játékos kezelés ───────────────────────────────────────────────────────

    /**
     * Hozzáad egy játékost a csapathoz.
     *
     * @param teamId a csapat egyedi azonosítója (UUID)
     * @param playerId a hozzáadni kívánt játékos egyedi azonosítója (UUID)
     */
    @Transactional
    public void addPlayerToTeam(UUID teamId, UUID playerId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("error.team.not_found", new Object[]{teamId}, "Team not found: " + teamId));
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new NotFoundException("error.player.not_found", new Object[]{playerId}, "Player not found: " + playerId));

        if (team.getPlayers().contains(player)) {
            throw new ConflictException("error.team.player.already_member", new Object[0], "Player is already a member of this team.");
        }

        player.addTeam(team);
        playerRepository.save(player);
    }

    /**
     * Eltávolít egy játékost a csapatból.
     *
     * @param teamId a csapat egyedi azonosítója (UUID)
     * @param playerId az eltávolítani kívánt játékos egyedi azonosítója (UUID)
     */
    @Transactional
    public void removePlayerFromTeam(UUID teamId, UUID playerId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("error.team.not_found", new Object[]{teamId}, "Team not found: " + teamId));
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new NotFoundException("error.player.not_found", new Object[]{playerId}, "Player not found: " + playerId));

        if (!team.getPlayers().contains(player)) {
            throw new ConflictException("error.team.player.not_member", new Object[0], "Player is not a member of this team.");
        }
        player.removeTeam(team);
        playerRepository.save(player);
    }

    // ── Coach kezelés ─────────────────────────────────────────────────────────

    /**
     * Hozzáad egy edzőt a csapathoz.
     *
     * @param teamId a csapat egyedi azonosítója (UUID)
     * @param coachId a hozzáadni kívánt edző egyedi azonosítója (UUID)
     */
    @Transactional
    public void addCoachToTeam(UUID teamId, UUID coachId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("error.team.not_found", new Object[]{teamId}, "Team not found: " + teamId));
        Coach coach = coachRepository.findById(coachId)
                .orElseThrow(() -> new NotFoundException("error.coach.not_found", new Object[]{coachId}, "Coach not found: " + coachId));

        if (team.getCoaches().contains(coach)) {
            throw new ConflictException("error.team.coach.already_member", new Object[0], "Coach is already a member of this team.");
        }

        coach.addTeam(team);
        coachRepository.save(coach);
    }

    /**
     * Eltávolít egy edzőt a csapatból.
     *
     * @param teamId a csapat egyedi azonosítója (UUID)
     * @param coachId az eltávolítani kívánt edző egyedi azonosítója (UUID)
     */
    @Transactional
    public void removeCoachFromTeam(UUID teamId, UUID coachId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("error.team.not_found", new Object[]{teamId}, "Team not found: " + teamId));
        Coach coach = coachRepository.findById(coachId)
                .orElseThrow(() -> new NotFoundException("error.coach.not_found", new Object[]{coachId}, "Coach not found: " + coachId));

        if (!team.getCoaches().contains(coach)) {
            throw new ConflictException("error.team.coach.not_member", new Object[0], "Coach is not a member of this team.");
        }

        coach.removeTeam(team);
        coachRepository.save(coach);
    }

    // ── Fan kezelés ─────────────────────────────────────────────────────────

    /**
     * Hozzáad egy szurkolót a csapathoz.
     *
     * @param teamId a csapat egyedi azonosítója (UUID)
     * @param fanId a hozzáadni kívánt szurkoló egyedi azonosítója (UUID)
     */
    @Transactional
    public void addFanToTeam(UUID teamId, UUID fanId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("error.team.not_found", new Object[]{teamId}, "Team not found: " + teamId));
        Fan fan = fanRepository.findById(fanId)
                .orElseThrow(() -> new NotFoundException("error.fan.not_found", new Object[]{fanId}, "Fan not found: " + fanId));

        if (fan.getTeams().contains(team)) {
            throw new ConflictException("error.team.fan.already_following", new Object[0], "Fan is already following this team.");
        }

        fan.addTeam(team);
        fanRepository.save(fan);
    }

    /**
     * Eltávolít egy szurkolót a csapatból.
     *
     * @param teamId a csapat egyedi azonosítója (UUID)
     * @param fanId az eltávolítani kívánt szurkoló egyedi azonosítója (UUID)
     */
    @Transactional
    public void removeFanFromTeam(UUID teamId, UUID fanId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("error.team.not_found", new Object[]{teamId}, "Team not found: " + teamId));
        Fan fan = fanRepository.findById(fanId)
                .orElseThrow(() -> new NotFoundException("error.fan.not_found", new Object[]{fanId}, "Fan not found: " + fanId));

        if (!fan.getTeams().contains(team)) {
            throw new ConflictException("error.team.fan.not_following", new Object[0], "Fan is not following this team.");
        }

        fan.removeTeam(team);
        fanRepository.save(fan);
    }

    /**
     * Feloldja az autentikált edzőt a JWT token alapján.
     * Először a Keycloak azonosító alapján keres, majd az email alapján.
     *
     * @param jwt a hitelesített felhasználó JWT tokenje
     * @return az edző entitása (Coach)
     */
    private Coach resolveCoach(Jwt jwt) {
        String email = resolveEmail(jwt);
        String subject = resolveSubject(jwt).orElse(null);

        return findCoachBySubject(subject)
                .or(() -> coachRepository.findByEmail(email))
                .orElseThrow(() -> new NotFoundException("error.coach.not_found", new Object[]{email}, "Coach not found for authenticated user: " + email));
    }

    /**
     * Kinyeri a JWT subject-jét UUID-ként, ha belső felhasználói azonosítót (fallback) tartalmaz.
     * Ha a subject hiányzik vagy nem UUID, üres Optionallal tér vissza.
     *
     * @param jwt a hitelesített felhasználó JWT tokenje
     * @return az azonosító (UUID) Optional formában
     */
    private Optional<UUID> resolveSubjectAsUuid(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(UUID.fromString(subject));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    /**
     * Kinyeri a felhasználó email címét a JWT tokenből.
     * Előnyben részesíti az 'email' mezőt, különben a 'preferred_username'-re hagyatkozik.
     *
     * @param jwt a hitelesített felhasználó JWT tokenje
     * @return a felhasználó email címe (String)
     */
    private String resolveEmail(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            email = jwt.getClaimAsString("preferred_username");
        }
        if (email == null || email.isBlank()) {
            throw new BadRequestException("error.auth.email_missing", new Object[0], "Authenticated token does not contain an email.");
        }
        return email;
    }

    // ── Entitás → DTO konverzió ───────────────────────────────────────────────

    /**
     * Átalakít egy csapat entitást válasz (DTO) formátumba.
     *
     * @param team az átalakítandó csapat entitása (Team)
     * @return a csapat válasz objektuma (TeamResponse)
     */
    private TeamResponse toResponse(Team team) {
        List<TeamResponse.MemberInfo> players = team.getPlayers().stream()
                .map(p -> new TeamResponse.MemberInfo(p.getId(), p.getFirstName(), p.getLastName()))
                .toList();

        List<TeamResponse.MemberInfo> coaches = team.getCoaches().stream()
                .map(c -> new TeamResponse.MemberInfo(c.getId(), c.getFirstName(), c.getLastName()))
                .toList();


        return new TeamResponse(
                team.getId(),
                team.getName(),
                team.getShortName(),
                team.getLogoUrl(),
                players,
                coaches
        );
    }

    /**
     * Ellenőrzi, hogy a hitelesítési objektum megfelelő JWT tokent tartalmaz-e.
     *
     * @param authentication a bejelentkezett felhasználó autentikációs objektuma
     * @return a JWT autentikációs token (JwtAuthenticationToken)
     */
    private JwtAuthenticationToken requireJwtAuthentication(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication) || jwtAuthentication.getToken() == null) {
            throw new UnauthorizedException("error.auth.unauthorized", new Object[0], "Authentication is required to access this resource.");
        }
        return jwtAuthentication;
    }

    /**
     * Feloldja a felhasználó jogosultsági szerepkörét az Authentication objektumból.
     *
     * @param authentication a bejelentkezett felhasználó autentikációs objektuma
     * @return a felhasználói szerepkör (UserRole)
     */
    private UserRole resolveRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .map(String::toUpperCase)
                .map(this::safeUserRole)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new BadRequestException("error.auth.role_missing", new Object[0], "Authenticated token does not contain a supported role."));
    }

    /**
     * Biztonságosan próbálja meg feloldani a szerepkört a megadott név alapján.
     *
     * @param roleName a szerepkör neve (String)
     * @return a feloldott szerepkör (UserRole), vagy null, ha nincs ilyen
     */
    private UserRole safeUserRole(String roleName) {
        try {
            return UserRole.valueOf(roleName);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Feloldja az autentikált játékost a JWT token alapján.
     * Először a Keycloak azonosító alapján keres, majd belső UUID, végül email alapján.
     *
     * @param jwt a hitelesített felhasználó JWT tokenje
     * @return a játékos entitása (Player)
     */
    private Player resolvePlayer(Jwt jwt) {
        String email = resolveEmail(jwt);
        String subject = resolveSubject(jwt).orElse(null);

        return findPlayerBySubject(subject)
            .or(() -> resolveSubjectAsUuid(jwt).flatMap(playerRepository::findById))
                .or(() -> playerRepository.findByEmail(email))
                .orElseThrow(() -> new NotFoundException("error.player.not_found", new Object[]{email}, "Player not found for authenticated user: " + email));
    }
    /**
     * Feloldja az autentikált szurkolót a JWT token alapján.
     * Először a Keycloak azonosító alapján keres, majd belső UUID, végül email alapján.
     *
     * @param jwt a hitelesített felhasználó JWT tokenje
     * @return a szurkoló entitása (Fan)
     */    private Fan resolveFan(Jwt jwt) {
        String email = resolveEmail(jwt);
        String subject = resolveSubject(jwt).orElse(null);

        return findFanBySubject(subject)
            .or(() -> resolveSubjectAsUuid(jwt).flatMap(fanRepository::findById))
                .or(() -> fanRepository.findByEmail(email))
                .orElseThrow(() -> new NotFoundException("error.fan.not_found", new Object[]{email}, "Fan not found for authenticated user: " + email));
    }

    /**
     * Kinyeri a subject-et a JWT tokenből.
     *
     * @param jwt a hitelesített felhasználó JWT tokenje
     * @return a subject (String) Optional formában
     */
    private Optional<String> resolveSubject(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(subject);
    }

    /**
     * Keres egy edzőt a subject (Keycloak ID) alapján.
     *
     * @param subject a keresett edző Keycloak azonosítója
     * @return az edző entitása (Coach) Optional formában
     */
    private Optional<Coach> findCoachBySubject(String subject) {
        return subject == null ? Optional.empty() : coachRepository.findByKeycloakId(subject);
    }

    /**
     * Keres egy játékost a subject (Keycloak ID) alapján.
     *
     * @param subject a keresett játékos Keycloak azonosítója
     * @return a játékos entitása (Player) Optional formában
     */
    private Optional<Player> findPlayerBySubject(String subject) {
        return subject == null ? Optional.empty() : playerRepository.findByKeycloakId(subject);
    }

    /**
     * Keres egy szurkolót a subject (Keycloak ID) alapján.
     *
     * @param subject a keresett szurkoló Keycloak azonosítója
     * @return a szurkoló entitása (Fan) Optional formában
     */
    private Optional<Fan> findFanBySubject(String subject) {
        return subject == null ? Optional.empty() : fanRepository.findByKeycloakId(subject);
    }
}
