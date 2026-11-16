package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.EnumSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Hats for tests that call services without a request token.
 *
 * A test states the hats an account holds with {@link #user(UserRole...)} or {@link #grant}; with a token in the
 * security context the token decides, exactly as in production ({@link ActorHats}). Accounts are matched by
 * object or by id, so a row loaded again from the database keeps its hats.
 */
public class TestHats extends ActorHats {

    private record Grant(User user, Set<UserRole> hats) {}

    private static final CopyOnWriteArrayList<Grant> GRANTS = new CopyOnWriteArrayList<>();

    /** A new, unsaved account holding these hats. */
    public static User user(UserRole... hats) {
        return grant(new User(), hats);
    }

    /** Sets (replaces) the hats of this account and returns it. */
    public static <T extends User> T grant(T user, UserRole... hats) {
        Objects.requireNonNull(user, "user");
        GRANTS.removeIf(grant -> grant.user() == user);
        Set<UserRole> set = EnumSet.noneOf(UserRole.class);
        for (UserRole hat : hats) {
            if (hat != null) {
                set.add(hat);
            }
        }
        GRANTS.add(new Grant(user, set));
        return user;
    }

    /** The granted hats alone, for hand-written test doubles of UserAccessService. */
    public static boolean has(User actor, UserRole hat) {
        return hat != null && granted(actor).contains(hat);
    }

    @Override
    public Set<UserRole> of(User actor) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            return super.of(actor);
        }
        return granted(actor);
    }

    private static Set<UserRole> granted(User actor) {
        if (actor == null) {
            return Set.of();
        }
        UUID id = actor.getId();
        // Latest grant wins, so a later grant for the same id replaces an earlier one.
        Set<UserRole> found = Set.of();
        for (Iterator<Grant> it = GRANTS.iterator(); it.hasNext(); ) {
            Grant grant = it.next();
            if (grant.user() == actor || (id != null && id.equals(grant.user().getId()))) {
                found = grant.hats();
            }
        }
        return found;
    }
}
