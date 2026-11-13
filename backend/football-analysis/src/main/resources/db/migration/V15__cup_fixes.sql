-- Kupakezelés javításai (docs/tervek/kupakezeles-bovites.md, 3.2).

-- 1.2: a kupát létrehozó felhasználó törölhető. A kupa megmarad, a létrehozó NULL lesz.
ALTER TABLE cups ALTER COLUMN created_by_id DROP NOT NULL;
ALTER TABLE cups DROP CONSTRAINT fkgab5vsb5fruvda20mi1uyub70;
ALTER TABLE cups
    ADD CONSTRAINT fk_cups_created_by FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE SET NULL;

-- CU12: azonos nevű csapat vagy kétszer ugyanaz a valódi csapat nem lehet egy kupában.
-- Meglévő duplikátumnál a migráció érthető üzenettel megáll, az adatot kézzel kell javítani.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM cup_teams GROUP BY cup_id, lower(trim(name)) HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'V15: van olyan kupa, amelyben két azonos nevű csapat szerepel. Javítsd kézzel, majd futtasd újra.';
    END IF;
    IF EXISTS (SELECT 1 FROM cup_teams WHERE real_team_id IS NOT NULL GROUP BY cup_id, real_team_id HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'V15: van olyan kupa, amelyben ugyanaz a valódi csapat kétszer szerepel. Javítsd kézzel, majd futtasd újra.';
    END IF;
END $$;

CREATE UNIQUE INDEX ux_cup_teams_name ON cup_teams (cup_id, lower(trim(name)));
CREATE UNIQUE INDEX ux_cup_teams_real_team ON cup_teams (cup_id, real_team_id) WHERE real_team_id IS NOT NULL;

-- 2.7, CU7: visszavágó. 1 = első meccs, 2 = visszavágó. Egy párosítás (bármelyik irányban) legfeljebb kétszer.
ALTER TABLE cup_matches ADD COLUMN leg smallint NOT NULL DEFAULT 1;
ALTER TABLE cup_matches ADD CONSTRAINT ck_cup_matches_leg CHECK (leg IN (1, 2));

-- Meglévő kétszeres párosításnál a később létrehozott meccs lesz a visszavágó.
WITH ranked AS (
    SELECT id,
           row_number() OVER (
               PARTITION BY cup_id, least(home_team_id, away_team_id), greatest(home_team_id, away_team_id)
               ORDER BY created_at, id) AS n
    FROM cup_matches
)
UPDATE cup_matches m SET leg = 2 FROM ranked r WHERE r.id = m.id AND r.n = 2;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM cup_matches
        GROUP BY cup_id, least(home_team_id, away_team_id), greatest(home_team_id, away_team_id)
        HAVING count(*) > 2
    ) THEN
        RAISE EXCEPTION 'V15: van olyan párosítás, amely háromszor vagy többször szerepel egy kupában. Javítsd kézzel, majd futtasd újra.';
    END IF;
END $$;

CREATE UNIQUE INDEX ux_cup_matches_pairing
    ON cup_matches (cup_id, least(home_team_id, away_team_id), greatest(home_team_id, away_team_id), leg);

-- Az új egyedi indexek cup_id-val kezdődnek, így a kupa szerinti keresést és a cups FK-ellenőrzést is kiszolgálják.
-- A csak cup_id-ra épülő régi indexek ezért fölöslegesek, csak az írást lassítanák.
DROP INDEX IF EXISTS idx_cupteam_cup_id;
DROP INDEX IF EXISTS idx_cupmatch_cup_id;

-- ---------------------------------------------------------------------------------------------
-- Lapozás és keresés indexei (nem a kupához tartoznak, csak ebbe a még ki nem adott migrációba kerültek).

-- A lapozott listák rendezése indexből jön, így a LIMIT/OFFSET nem rendezi minden lapnál az egész táblát.
-- A kifejezéseknek pontosan egyezniük kell a lekérdezések ORDER BY-ával (UserRepository, ClubRepository, TeamRepository).
CREATE INDEX ix_users_name_order ON users (last_name, first_name, id);
CREATE INDEX ix_clubs_name_order ON clubs (lower(name), id);
CREATE INDEX ix_teams_name_order ON teams (lower(name), id);

-- Szövegrészre keresés ('%szöveg%') a felhasználók között: trigram GIN index, mert a B-tree ezt nem tudja.
-- A kifejezések betűre egyeznek a UserSearchSql.NAME_OR_EMAIL_MATCH feltételeivel: teljes név mindkét
-- sorrendben (a felület nyelvtől függően így írja ki) és e-mail. Legalább 3 karakteres keresésnél segít.
-- A csapatkatalógus keresése azért nem kap ilyet, mert OR-ral a joinolt klubnévre is szűr, ott a planner nem használná.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX ix_users_full_name_trgm ON users
    USING gin (lower(coalesce(last_name, '') || ' ' || coalesce(first_name, '')) gin_trgm_ops);
CREATE INDEX ix_users_full_name_reversed_trgm ON users
    USING gin (lower(coalesce(first_name, '') || ' ' || coalesce(last_name, '')) gin_trgm_ops);
CREATE INDEX ix_users_email_trgm ON users USING gin (lower(email) gin_trgm_ops);
