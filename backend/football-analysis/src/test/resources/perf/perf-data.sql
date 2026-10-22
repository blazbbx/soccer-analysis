-- Large synthetic data set for QueryPerformanceIT (see docs/teljesitmenymeres.md). Ids are md5-derived so tables can reference each other.
\timing on
SET synchronous_commit = off;

CREATE OR REPLACE FUNCTION uid(kind text, n bigint) RETURNS uuid
    LANGUAGE sql IMMUTABLE AS $$ SELECT md5(kind || ':' || n)::uuid $$;

-- 300 clubs, 3000 teams (10 per club)
INSERT INTO clubs (id, name, short_name, created_at)
SELECT uid('club', i), 'Club ' || i || ' ' || (ARRAY['Vasas','Ferencváros','Újpest','Honvéd','Diósgyőr','Győri','Pécsi','Szegedi'])[1 + i % 8], 'C' || i,
       now() - (i || ' days')::interval
FROM generate_series(1, 300) i;

INSERT INTO teams (id, name, short_name, club_id)
SELECT uid('team', i), 'Team ' || i || ' ' || (ARRAY['U9','U11','U13','U15','U17','U19','Felnőtt','Öregfiúk','Női','Futsal'])[1 + i % 10],
       'T' || i, uid('club', 1 + (i - 1) / 10)
FROM generate_series(1, 3000) i;

-- Users: 5 admins, 300 club admins, 6000 coaches, 20000 players, 30000 parents, 20000 fans
INSERT INTO users (user_type, id, created_at, email, first_name, last_name, role)
SELECT r.role, uid(lower(r.role), i), now() - ((i % 1500) || ' days')::interval,
       lower(r.role) || i || '@perf.test',
       (ARRAY['Anna','Bence','Csaba','Dóra','Eszter','Ferenc','Gábor','Hanna','István','Júlia','Kata','László','Máté','Nóra','Olivér','Petra'])[1 + i % 16],
       (ARRAY['Kovács','Szabó','Tóth','Horváth','Varga','Kiss','Molnár','Németh','Farkas','Balogh','Papp','Takács','Juhász','Lakatos','Mészáros','Oláh','Simon','Rácz','Fekete','Szilágyi'])[1 + (i / 16) % 20] || ' ' || i,
       r.role
FROM (VALUES ('ADMIN', 5), ('CLUB_ADMIN', 300), ('COACH', 6000), ('PLAYER', 20000), ('PARENT', 30000), ('FAN', 20000)) r(role, n),
     LATERAL generate_series(1, r.n) i;

INSERT INTO club_admins (user_id, club_id) SELECT uid('club_admin', i), uid('club', i) FROM generate_series(1, 300) i;
INSERT INTO coach_teams (coach_id, team_id) SELECT uid('coach', i), uid('team', 1 + (i - 1) % 3000) FROM generate_series(1, 6000) i;
INSERT INTO fan_favorite_teams (fan_id, team_id)
SELECT uid('fan', i), uid('team', 1 + (i * k * 7) % 3000) FROM generate_series(1, 20000) i, generate_series(1, 2) k
ON CONFLICT DO NOTHING;

-- 60000 player profiles (20 per team); the first 20000 have a login
INSERT INTO player_profiles (id, first_name, last_name, date_of_birth, linked_user_id, created_at, can_offer_rides)
SELECT uid('profile', i), 'Gyerek', 'Játékos ' || i, date '2008-01-01' + (i % 4000),
       CASE WHEN i <= 20000 THEN uid('player', i) END, now(), i % 5 = 0
FROM generate_series(1, 60000) i;
INSERT INTO team_roster (player_profile_id, team_id)
SELECT uid('profile', i), uid('team', 1 + (i - 1) / 20) FROM generate_series(1, 60000) i;
-- Every 10th profile also plays for a second team
INSERT INTO team_roster (player_profile_id, team_id)
SELECT uid('profile', i), uid('team', 1 + ((i - 1) / 20 + 7) % 3000) FROM generate_series(10, 60000, 10) i
ON CONFLICT DO NOTHING;
-- Parents: profiles 1..45000 get one, every 3rd a second parent
INSERT INTO player_parents (user_id, player_profile_id)
SELECT uid('parent', 1 + (i - 1) % 30000), uid('profile', i) FROM generate_series(1, 45000) i;
INSERT INTO player_parents (user_id, player_profile_id)
SELECT uid('parent', 1 + (i + 12345) % 30000), uid('profile', i) FROM generate_series(3, 45000, 3) i
ON CONFLICT DO NOTHING;

-- 150000 matches over 5 years (50 per team), 300000 clips
INSERT INTO matches (id, created_at, home_team_id, away_team_id, uploaded_by_user_id, match_date, overall_status,
                     saved_minio_file_name, original_file_name, home_score, away_score)
SELECT uid('match', i), now() - ((i % 1825) || ' days')::interval - ((i % 1440) || ' minutes')::interval,
       uid('team', 1 + (i - 1) % 3000), CASE WHEN i % 4 = 0 THEN uid('team', 1 + (i * 13) % 3000) END,
       uid('coach', 1 + (i - 1) % 6000), now() - ((i % 1825) || ' days')::interval, 'COMPLETED',
       uid('match', i) || '.mp4', 'video' || i || '.mp4', i % 5, i % 3
FROM generate_series(1, 150000) i;
INSERT INTO clips (id, created_at, name, render_status, coach_id, match_id)
SELECT uid('clip', i), now() - ((i % 1825) || ' days')::interval, 'Clip ' || i, 'DONE',
       uid('coach', 1 + (i - 1) % 6000), uid('match', 1 + (i - 1) % 150000)
FROM generate_series(1, 300000) i;

-- 3 million chat messages (1000 per team)
INSERT INTO team_messages (id, content, created_at, team_id, sender_id)
SELECT uid('msg', i), 'Üzenet ' || i, now() - ((i / 3000) || ' hours')::interval,
       uid('team', 1 + (i - 1) % 3000), uid('coach', 1 + (i - 1) % 6000)
FROM generate_series(1, 3000000) i;

-- 300000 team events (100 per team, ~4 years back and 3 months ahead)
INSERT INTO team_events (id, team_id, type, starts_at, meeting_at, venue, meeting_point, created_by_user_id, created_at, updated_at)
SELECT uid('event', i), uid('team', 1 + (i - 1) % 3000),
       (ARRAY['MATCH','TRAINING','TOURNAMENT'])[1 + i % 3],
       date_trunc('hour', now()) + (((i - 1) / 3000 - 85) * 15 || ' days')::interval,
       date_trunc('hour', now()) + (((i - 1) / 3000 - 85) * 15 || ' days')::interval - interval '1 hour',
       'Pálya ' || i % 50, 'Kapu', uid('coach', 1 + (i - 1) % 6000), now(), now()
FROM generate_series(1, 300000) i;

-- Attendance: every roster member answers every event of their first team (6 million rows)
INSERT INTO event_attendances (event_id, player_profile_id, status, responded_at, wants_ride)
SELECT e.id, r.player_profile_id, (ARRAY['GOING','NOT_GOING','MAYBE'])[1 + abs(hashtext(e.id::text || r.player_profile_id::text)) % 3],
       now(), false
FROM team_events e
JOIN team_roster r ON r.team_id = e.team_id
WHERE r.player_profile_id IN (SELECT uid('profile', i) FROM generate_series(1, 60000) i);

-- Rides: 2 offers per event, 2 seats per offer
INSERT INTO ride_offers (id, event_id, driver_user_id, seats_total, departure_place, departure_at, created_at, updated_at)
SELECT uid('offer', (n - 1) * 2 + k), uid('event', n), uid('parent', 1 + ((n - 1) * 2 + k) % 30000), 4, 'Iskola',
       now(), now(), now()
FROM generate_series(1, 300000) n, generate_series(1, 2) k;
INSERT INTO ride_seats (id, offer_id, event_id, player_profile_id, booked_by_user_id, created_at)
SELECT uid('seat', (o - 1) * 2 + s), uid('offer', o), uid('event', 1 + (o - 1) / 2),
       uid('profile', 1 + (((1 + (o - 1) / 2 - 1) % 3000) * 20) + ((o * 2 + s) % 20)), NULL, now()
FROM generate_series(1, 600000) o, generate_series(1, 2) s
ON CONFLICT DO NOTHING;

-- 3000 cups, 8 teams and 28 matches each
INSERT INTO cups (id, created_at, name, created_by_id)
SELECT uid('cup', i), now() - ((i % 1500) || ' days')::interval, 'Kupa ' || i, uid('coach', 1 + (i - 1) % 6000)
FROM generate_series(1, 3000) i;
INSERT INTO cup_teams (id, created_at, name, cup_id, real_team_id)
SELECT uid('cupteam', (c - 1) * 8 + k), now(), 'Csapat ' || k, uid('cup', c), uid('team', 1 + (c * 8 + k) % 3000)
FROM generate_series(1, 3000) c, generate_series(1, 8) k;
INSERT INTO cup_matches (id, created_at, played, scheduled_at, home_team_id, away_team_id, cup_id, home_score, away_score)
SELECT uid('cupmatch', (c - 1) * 100 + h * 8 + a), now(), true, now(),
       uid('cupteam', (c - 1) * 8 + h), uid('cupteam', (c - 1) * 8 + a), uid('cup', c), h % 4, a % 3
FROM generate_series(1, 3000) c, generate_series(1, 8) h, generate_series(1, 8) a
WHERE h < a;

-- 1 million audit events, 30000 invites
INSERT INTO audit_events (id, actor_user_id, actor_user_role, created_at, details, event_type, target_id, target_type)
SELECT uid('audit', i), uid('coach', 1 + i % 6000), 'COACH', now() - ((i / 500) || ' hours')::interval, 'x',
       (ARRAY['MATCH_UPLOADED','TEAM_MEMBER_ADDED','RIDE_OFFER_DELETED'])[1 + i % 3], uid('team', 1 + i % 3000)::text, 'TEAM'
FROM generate_series(1, 1000000) i;
INSERT INTO team_invitations (id, created_at, created_by_user_id, created_by_user_role, expires_at, invited_role, max_uses,
                              token, used_count, team_id, invite_kind)
SELECT uid('invite', i), now(), uid('coach', 1 + i % 6000), 'COACH', now() + interval '7 days', 'PLAYER', 10,
       md5('token' || i), 0, uid('team', 1 + i % 3000), 'PLAYER_SELF'
FROM generate_series(1, 30000) i;

ANALYZE;
SELECT relname, n_live_tup FROM pg_stat_user_tables ORDER BY n_live_tup DESC LIMIT 12;
