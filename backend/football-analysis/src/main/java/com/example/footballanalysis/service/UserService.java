package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.FieldConflictException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.db.user.*;
import com.example.footballanalysis.model.requests.CreateUserRequest;
import com.example.footballanalysis.model.responses.UserResponse;
import com.example.footballanalysis.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /**
     * Visszaadja az összes regisztrált felhasználót a rendszerből.
     *
     * @return a felhasználók listája DTO formátumban
     */
    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream().map(this::toResponse).toList();
    }

    /**
     * Lekérdez egy adott felhasználót az egyedi azonosítója (UUID) alapján.
     *
     * @param id a keresett felhasználó egyedi azonosítója
     * @return a megtalált felhasználó adatai DTO formátumban
     * @throws NotFoundException ha a megadott azonosítóval nem található felhasználó
     */
    @Transactional(readOnly = true)
    public UserResponse getUser(UUID id) {
        return toResponse(userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("error.user.not_found", new Object[]{id}, "User not found: " + id)));
    }

    /**
     * Új felhasználót hoz létre a rendszerben a megadott adatok alapján.
     * Ellenőrzi, hogy az e-mail cím foglalt-e, beállítja a kért jogosultsági szintet (szerepkört),
     * és elmenti az új entitást az adatbázisba.
     *
     * @param req a létrehozandó felhasználó adatait tartalmazó kérés (DTO)
     * @return a sikeresen létrehozott felhasználó adatai
     * @throws FieldConflictException ha a megadott e-mail cím már létezik a rendszerben
     * @throws BadRequestException ha a megadott szerepkör (role) ismeretlen
     */
    @Transactional
    public UserResponse createUser(CreateUserRequest req) {
        // Formátum/jelenlét validáció: a @Valid annotáció a Controller rétegben elvégzi.
        // Itt csak az üzleti szabályokat ellenőrizzük.
        if (userRepository.findByEmail(req.email()).isPresent()) {
            throw new FieldConflictException("email",
                    "error.user.email.conflict", new Object[]{req.email()},
                    "Email already in use: " + req.email());
        }

        User user = switch (req.role().toUpperCase()) {
            case "ADMIN"  -> { var u = new Admin();  u.setRole(UserRole.ADMIN);  yield u; }
            case "PLAYER" -> { var u = new Player(); u.setRole(UserRole.PLAYER); yield u; }
            case "COACH"  -> { var u = new Coach();  u.setRole(UserRole.COACH);  yield u; }
            case "FAN"    -> { var u = new Fan();    u.setRole(UserRole.FAN);    yield u; }
            default -> throw new BadRequestException("Unknown role: " + req.role());
        };

        user.setEmail(req.email());
        user.setFirstName(req.firstName().trim());
        user.setLastName(req.lastName().trim());

        return toResponse(userRepository.save(user));
    }

    /**
     * Töröl egy felhasználót a rendszerből a megadott azonosító alapján.
     *
     * @param id a törlendő felhasználó egyedi azonosítója
     * @throws NotFoundException ha a felhasználó nem létezik az adott UUID-vel
     */
    @Transactional
    public void deleteUser(UUID id) {
        if (!userRepository.existsById(id)) {
            throw new NotFoundException("error.user.not_found", new Object[]{id}, "User not found: " + id);
        }
        userRepository.deleteById(id);
    }

    /**
     * Belső segédmetódus, amely egy adatbázisos JPA User entitást alakít át a válaszhoz
     * használt UserResponse DTO objektummá. A felhasználó pontos alosztályától
     * (Player, Coach, Fan) függően kinyeri a vonatkozó csapattagságokat is.
     *
     * @param user az adatbázisból kiolvasott User entitás
     * @return a REST interfészen visszaadható DTO objektum
     */
    // ── Entitás → DTO konverzió ───────────────────────────────────────────────
    private UserResponse toResponse(User user) {
        List<UserResponse.TeamInfo> teams = null;
        if (user instanceof Player p) {
            teams = p.getTeams().stream()
                    .map(t -> new UserResponse.TeamInfo(t.getId(), t.getName()))
                    .toList();
        } else if (user instanceof Coach c) {
            teams = c.getTeams().stream()
                    .map(t -> new UserResponse.TeamInfo(t.getId(), t.getName()))
                    .toList();
        } else if (user instanceof Fan f) {
            teams = f.getTeams().stream()
                    .map(t -> new UserResponse.TeamInfo(t.getId(), t.getName()))
                    .toList();
        }
        // Admin esetén teams = null → nem kerül bele a JSON-ba (ha @JsonInclude(NON_NULL) van)

        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole(),
                user.getCreatedAt(),
                teams
        );
    }
}
