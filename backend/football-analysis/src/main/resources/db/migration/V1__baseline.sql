-- Full schema as of the squash on 2026-10-22: the Hibernate-built tables plus the old V1-V20
-- migrations. Replaces those files, so an empty database can be migrated from scratch.
-- Later schema changes go into new migrations (V2, V3, ...), Hibernate only validates.

CREATE TABLE audit_events (
    id uuid NOT NULL,
    actor_user_id uuid,
    actor_user_role character varying(20),
    created_at timestamp(6) without time zone NOT NULL,
    details text,
    event_type character varying(100) NOT NULL,
    target_id character varying(64),
    target_type character varying(50) NOT NULL
);

CREATE TABLE clips (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    name character varying(255),
    render_error character varying(1000),
    render_status character varying(50),
    rendered_at timestamp(6) without time zone,
    coach_id uuid,
    match_id uuid NOT NULL
);

CREATE TABLE club_admin_invites (
    id uuid NOT NULL,
    club_id uuid NOT NULL,
    created_by_user_id uuid NOT NULL,
    token character varying(64) NOT NULL,
    max_uses integer NOT NULL,
    used_count integer NOT NULL,
    expires_at timestamp without time zone NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE club_admins (
    user_id uuid NOT NULL,
    club_id uuid NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE clubs (
    id uuid NOT NULL,
    name character varying(255) NOT NULL,
    short_name character varying(255),
    logo_url character varying(255),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by_user_id uuid
);

CREATE TABLE coach_teams (
    coach_id uuid NOT NULL,
    team_id uuid NOT NULL
);

CREATE TABLE cup_matches (
    id uuid NOT NULL,
    away_score integer,
    created_at timestamp(6) without time zone NOT NULL,
    home_score integer,
    played boolean NOT NULL,
    scheduled_at timestamp(6) without time zone NOT NULL,
    away_team_id uuid NOT NULL,
    cup_id uuid NOT NULL,
    home_team_id uuid NOT NULL,
    real_match_id uuid
);

CREATE TABLE cup_teams (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    name character varying(100) NOT NULL,
    cup_id uuid NOT NULL,
    real_team_id uuid
);

CREATE TABLE cups (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    name character varying(100) NOT NULL,
    created_by_id uuid NOT NULL
);

CREATE TABLE event_attendances (
    event_id uuid NOT NULL,
    player_profile_id uuid NOT NULL,
    status character varying(20) NOT NULL,
    responded_by_user_id uuid,
    responded_at timestamp without time zone,
    comment character varying(500),
    wants_ride boolean DEFAULT false NOT NULL,
    CONSTRAINT ck_event_attendances_status CHECK (((status)::text = ANY (ARRAY[('GOING'::character varying)::text, ('NOT_GOING'::character varying)::text, ('MAYBE'::character varying)::text, ('NO_RESPONSE'::character varying)::text])))
);

CREATE TABLE fan_favorite_teams (
    fan_id uuid NOT NULL,
    team_id uuid NOT NULL
);

CREATE TABLE match_squad_members (
    id uuid NOT NULL,
    comment character varying(255),
    jersey_number integer NOT NULL,
    match_id uuid NOT NULL,
    player_id uuid NOT NULL,
    team_id uuid NOT NULL
);

CREATE TABLE matches (
    id uuid NOT NULL,
    away_score integer,
    away_team_color character varying(100),
    away_team_name character varying(255),
    created_at timestamp(6) without time zone,
    defished_image_url character varying(255),
    encoding_status character varying(255),
    field_corners_json text,
    field_detection_status character varying(255),
    hls_manifest_url character varying(255),
    home_score integer,
    home_team_color character varying(100),
    match_date timestamp(6) without time zone,
    ml_status character varying(255),
    original_file_name character varying(255),
    overall_status character varying(255),
    referee_color character varying(100),
    saved_minio_file_name character varying(255),
    tracking_data_url character varying(255),
    away_team_id uuid,
    home_team_id uuid,
    uploaded_by_user_id uuid
);

CREATE TABLE parent_invites (
    id uuid NOT NULL,
    player_profile_id uuid NOT NULL,
    created_by_user_id uuid NOT NULL,
    token character varying(64) NOT NULL,
    max_uses integer NOT NULL,
    used_count integer NOT NULL,
    expires_at timestamp without time zone NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE player_parents (
    user_id uuid NOT NULL,
    player_profile_id uuid NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE player_profiles (
    id uuid NOT NULL,
    first_name character varying(255) NOT NULL,
    last_name character varying(255) NOT NULL,
    date_of_birth date,
    linked_user_id uuid,
    medical_valid_until date,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    can_offer_rides boolean DEFAULT false NOT NULL
);

CREATE TABLE ride_offers (
    id uuid NOT NULL,
    event_id uuid NOT NULL,
    driver_user_id uuid NOT NULL,
    seats_total integer NOT NULL,
    departure_place character varying(255) NOT NULL,
    departure_at timestamp without time zone NOT NULL,
    notes character varying(500),
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL,
    CONSTRAINT ck_ride_offers_seats CHECK (((seats_total >= 1) AND (seats_total <= 8)))
);

CREATE TABLE ride_seats (
    id uuid NOT NULL,
    offer_id uuid NOT NULL,
    event_id uuid NOT NULL,
    player_profile_id uuid NOT NULL,
    booked_by_user_id uuid,
    created_at timestamp without time zone NOT NULL
);

CREATE TABLE team_events (
    id uuid NOT NULL,
    team_id uuid NOT NULL,
    type character varying(20) NOT NULL,
    starts_at timestamp without time zone NOT NULL,
    meeting_at timestamp without time zone NOT NULL,
    venue character varying(255),
    meeting_point character varying(255) NOT NULL,
    notes character varying(2000),
    rsvp_deadline timestamp without time zone,
    video_match_id uuid,
    cup_match_id uuid,
    created_by_user_id uuid,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL,
    CONSTRAINT ck_team_events_type CHECK (((type)::text = ANY (ARRAY[('MATCH'::character varying)::text, ('TRAINING'::character varying)::text, ('TOURNAMENT'::character varying)::text])))
);

CREATE TABLE team_invitations (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by_user_id uuid NOT NULL,
    created_by_user_role character varying(20) NOT NULL,
    expires_at timestamp(6) without time zone NOT NULL,
    invited_role character varying(20) NOT NULL,
    max_uses integer NOT NULL,
    token character varying(64) NOT NULL,
    used_count integer NOT NULL,
    team_id uuid NOT NULL,
    player_profile_id uuid,
    invite_kind character varying(32) NOT NULL,
    pending_registrations text,
    CONSTRAINT team_invitations_created_by_user_role_check CHECK (((created_by_user_role)::text = ANY (ARRAY[('ADMIN'::character varying)::text, ('PLAYER'::character varying)::text, ('COACH'::character varying)::text, ('FAN'::character varying)::text, ('PARENT'::character varying)::text, ('CLUB_ADMIN'::character varying)::text]))),
    CONSTRAINT team_invitations_invited_role_check CHECK (((invited_role)::text = ANY (ARRAY[('ADMIN'::character varying)::text, ('PLAYER'::character varying)::text, ('COACH'::character varying)::text, ('FAN'::character varying)::text, ('PARENT'::character varying)::text])))
);

CREATE TABLE team_messages (
    id uuid NOT NULL,
    content text NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    team_id uuid NOT NULL,
    sender_id uuid NOT NULL
);

CREATE TABLE team_roster (
    player_profile_id uuid NOT NULL,
    team_id uuid NOT NULL
);

CREATE TABLE teams (
    id uuid NOT NULL,
    logo_url character varying(255),
    name character varying(255) NOT NULL,
    short_name character varying(255),
    club_id uuid
);

CREATE TABLE users (
    user_type character varying(31) NOT NULL,
    id uuid NOT NULL,
    created_at timestamp(6) without time zone,
    email character varying(255),
    first_name character varying(255),
    last_name character varying(255),
    role character varying(255),
    CONSTRAINT users_role_check CHECK (((role)::text = ANY (ARRAY[('ADMIN'::character varying)::text, ('PLAYER'::character varying)::text, ('COACH'::character varying)::text, ('FAN'::character varying)::text, ('PARENT'::character varying)::text, ('CLUB_ADMIN'::character varying)::text]))),
    CONSTRAINT users_user_type_check CHECK (((user_type)::text = ANY (ARRAY[('ADMIN'::character varying)::text, ('PLAYER'::character varying)::text, ('COACH'::character varying)::text, ('FAN'::character varying)::text, ('PARENT'::character varying)::text, ('CLUB_ADMIN'::character varying)::text])))
);

ALTER TABLE ONLY audit_events
    ADD CONSTRAINT audit_events_pkey PRIMARY KEY (id);

ALTER TABLE ONLY clips
    ADD CONSTRAINT clips_pkey PRIMARY KEY (id);

ALTER TABLE ONLY club_admin_invites
    ADD CONSTRAINT club_admin_invites_pkey PRIMARY KEY (id);

ALTER TABLE ONLY club_admins
    ADD CONSTRAINT club_admins_pkey PRIMARY KEY (user_id, club_id);

ALTER TABLE ONLY clubs
    ADD CONSTRAINT clubs_pkey PRIMARY KEY (id);

ALTER TABLE ONLY coach_teams
    ADD CONSTRAINT coach_teams_pkey PRIMARY KEY (coach_id, team_id);

ALTER TABLE ONLY cup_matches
    ADD CONSTRAINT cup_matches_pkey PRIMARY KEY (id);

ALTER TABLE ONLY cup_teams
    ADD CONSTRAINT cup_teams_pkey PRIMARY KEY (id);

ALTER TABLE ONLY cups
    ADD CONSTRAINT cups_pkey PRIMARY KEY (id);

ALTER TABLE ONLY event_attendances
    ADD CONSTRAINT event_attendances_pkey PRIMARY KEY (event_id, player_profile_id);

ALTER TABLE ONLY fan_favorite_teams
    ADD CONSTRAINT fan_favorite_teams_pkey PRIMARY KEY (fan_id, team_id);

ALTER TABLE ONLY team_invitations
    ADD CONSTRAINT idx_team_invitations_token UNIQUE (token);

ALTER TABLE ONLY match_squad_members
    ADD CONSTRAINT match_squad_members_pkey PRIMARY KEY (id);

ALTER TABLE ONLY matches
    ADD CONSTRAINT matches_pkey PRIMARY KEY (id);

ALTER TABLE ONLY parent_invites
    ADD CONSTRAINT parent_invites_pkey PRIMARY KEY (id);

ALTER TABLE ONLY player_parents
    ADD CONSTRAINT player_parents_pkey PRIMARY KEY (user_id, player_profile_id);

ALTER TABLE ONLY player_profiles
    ADD CONSTRAINT player_profiles_pkey PRIMARY KEY (id);

ALTER TABLE ONLY ride_offers
    ADD CONSTRAINT ride_offers_pkey PRIMARY KEY (id);

ALTER TABLE ONLY ride_seats
    ADD CONSTRAINT ride_seats_pkey PRIMARY KEY (id);

ALTER TABLE ONLY team_events
    ADD CONSTRAINT team_events_pkey PRIMARY KEY (id);

ALTER TABLE ONLY team_invitations
    ADD CONSTRAINT team_invitations_pkey PRIMARY KEY (id);

ALTER TABLE ONLY team_messages
    ADD CONSTRAINT team_messages_pkey PRIMARY KEY (id);

ALTER TABLE ONLY team_roster
    ADD CONSTRAINT team_roster_pkey PRIMARY KEY (player_profile_id, team_id);

ALTER TABLE ONLY teams
    ADD CONSTRAINT teams_pkey PRIMARY KEY (id);

ALTER TABLE ONLY teams
    ADD CONSTRAINT uka510no6sjwqcx153yd5sm4jrr UNIQUE (name);

ALTER TABLE ONLY club_admin_invites
    ADD CONSTRAINT uq_club_admin_invites_token UNIQUE (token);

ALTER TABLE ONLY parent_invites
    ADD CONSTRAINT uq_parent_invites_token UNIQUE (token);

ALTER TABLE ONLY users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);

ALTER TABLE ONLY ride_offers
    ADD CONSTRAINT ux_ride_offers_event_driver UNIQUE (event_id, driver_user_id);

ALTER TABLE ONLY ride_offers
    ADD CONSTRAINT ux_ride_offers_id_event UNIQUE (id, event_id);

ALTER TABLE ONLY ride_seats
    ADD CONSTRAINT ux_ride_seats_event_profile UNIQUE (event_id, player_profile_id);

CREATE INDEX idx_audit_events_created_at ON audit_events USING btree (created_at);

CREATE INDEX idx_audit_events_event_type ON audit_events USING btree (event_type);

CREATE INDEX idx_audit_events_target ON audit_events USING btree (target_type, target_id);

CREATE INDEX idx_cup_created_at ON cups USING btree (created_at);

CREATE INDEX idx_cup_created_by ON cups USING btree (created_by_id);

CREATE INDEX idx_cupmatch_cup_id ON cup_matches USING btree (cup_id);

CREATE INDEX idx_cupmatch_home_away ON cup_matches USING btree (home_team_id, away_team_id);

CREATE INDEX idx_cupmatch_played ON cup_matches USING btree (played);

CREATE INDEX idx_cupteam_cup_id ON cup_teams USING btree (cup_id);

CREATE INDEX idx_team_messages_created_at ON team_messages USING btree (created_at);

CREATE INDEX idx_team_messages_team_id ON team_messages USING btree (team_id);

CREATE INDEX ix_clips_match_created ON clips USING btree (match_id, created_at DESC, id DESC);

CREATE INDEX ix_club_admin_invites_club ON club_admin_invites USING btree (club_id);

CREATE INDEX ix_club_admins_club_id ON club_admins USING btree (club_id);

CREATE INDEX ix_coach_teams_team_id_coach_id ON coach_teams USING btree (team_id, coach_id);

CREATE INDEX ix_event_attendances_profile ON event_attendances USING btree (player_profile_id);

CREATE INDEX ix_fan_favorite_teams_team_id_fan_id ON fan_favorite_teams USING btree (team_id, fan_id);

CREATE INDEX ix_matches_away_created ON matches USING btree (away_team_id, created_at DESC, id DESC);

CREATE INDEX ix_matches_created_at_id ON matches USING btree (created_at DESC, id DESC);

CREATE INDEX ix_matches_home_created ON matches USING btree (home_team_id, created_at DESC, id DESC);

CREATE INDEX ix_matches_uploaded_by ON matches USING btree (uploaded_by_user_id);

CREATE INDEX ix_parent_invites_profile ON parent_invites USING btree (player_profile_id);

CREATE INDEX ix_player_parents_profile ON player_parents USING btree (player_profile_id);

CREATE INDEX ix_ride_offers_event_id ON ride_offers USING btree (event_id);

CREATE INDEX ix_ride_seats_offer_event ON ride_seats USING btree (offer_id, event_id);

CREATE INDEX ix_ride_seats_profile ON ride_seats USING btree (player_profile_id);

CREATE INDEX ix_team_events_team_starts ON team_events USING btree (team_id, starts_at, id);

CREATE INDEX ix_team_messages_team_created_id ON team_messages USING btree (team_id, created_at DESC, id DESC);

CREATE INDEX ix_team_roster_team_id ON team_roster USING btree (team_id);

CREATE INDEX ix_teams_club_id ON teams USING btree (club_id);

CREATE UNIQUE INDEX ux_matches_saved_minio_file_name ON matches USING btree (saved_minio_file_name);

CREATE UNIQUE INDEX ux_player_profiles_linked_user ON player_profiles USING btree (linked_user_id);

CREATE UNIQUE INDEX ux_users_email ON users USING btree (email);

ALTER TABLE ONLY team_messages
    ADD CONSTRAINT fk11pan51hutc45ab4l0uuyy49m FOREIGN KEY (sender_id) REFERENCES users(id);

ALTER TABLE ONLY cup_matches
    ADD CONSTRAINT fk22hut1ps8jx60baafhbgps3qo FOREIGN KEY (cup_id) REFERENCES cups(id);

ALTER TABLE ONLY matches
    ADD CONSTRAINT fk2e8erbfecb0tjtq9iudg36bxu FOREIGN KEY (away_team_id) REFERENCES teams(id);

ALTER TABLE ONLY clips
    ADD CONSTRAINT fk3scw4dydh2tejcsj0oy086n18 FOREIGN KEY (coach_id) REFERENCES users(id);

ALTER TABLE ONLY coach_teams
    ADD CONSTRAINT fk4l53lrlah203qkwdvmc4gvopk FOREIGN KEY (team_id) REFERENCES teams(id);

ALTER TABLE ONLY cup_matches
    ADD CONSTRAINT fk51h8bwtl13s2l2694o209hish FOREIGN KEY (home_team_id) REFERENCES cup_teams(id);

ALTER TABLE ONLY match_squad_members
    ADD CONSTRAINT fk5x3omd16m4g7ut5r5iyq623yp FOREIGN KEY (match_id) REFERENCES matches(id);

ALTER TABLE ONLY cup_matches
    ADD CONSTRAINT fk8a2518b8vo1w2g0oc2pu6y39g FOREIGN KEY (real_match_id) REFERENCES matches(id);

ALTER TABLE ONLY matches
    ADD CONSTRAINT fk8k68nekawp47js52dq8720voe FOREIGN KEY (home_team_id) REFERENCES teams(id);

ALTER TABLE ONLY match_squad_members
    ADD CONSTRAINT fk93n5qq4qd8qpiwdd2j01976dt FOREIGN KEY (player_id) REFERENCES users(id);

ALTER TABLE ONLY cup_teams
    ADD CONSTRAINT fk95mcwid2oj87dcjw6egqdimm6 FOREIGN KEY (real_team_id) REFERENCES teams(id);

ALTER TABLE ONLY fan_favorite_teams
    ADD CONSTRAINT fk9fyb9mpygibphtnjynow3twl6 FOREIGN KEY (fan_id) REFERENCES users(id);

ALTER TABLE ONLY club_admin_invites
    ADD CONSTRAINT fk_club_admin_invites_club FOREIGN KEY (club_id) REFERENCES clubs(id) ON DELETE CASCADE;

ALTER TABLE ONLY club_admins
    ADD CONSTRAINT fk_club_admins_club FOREIGN KEY (club_id) REFERENCES clubs(id) ON DELETE CASCADE;

ALTER TABLE ONLY club_admins
    ADD CONSTRAINT fk_club_admins_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE ONLY event_attendances
    ADD CONSTRAINT fk_event_attendances_event FOREIGN KEY (event_id) REFERENCES team_events(id) ON DELETE CASCADE;

ALTER TABLE ONLY event_attendances
    ADD CONSTRAINT fk_event_attendances_profile FOREIGN KEY (player_profile_id) REFERENCES player_profiles(id) ON DELETE CASCADE;

ALTER TABLE ONLY event_attendances
    ADD CONSTRAINT fk_event_attendances_responded_by FOREIGN KEY (responded_by_user_id) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE ONLY parent_invites
    ADD CONSTRAINT fk_parent_invites_profile FOREIGN KEY (player_profile_id) REFERENCES player_profiles(id);

ALTER TABLE ONLY player_parents
    ADD CONSTRAINT fk_player_parents_profile FOREIGN KEY (player_profile_id) REFERENCES player_profiles(id);

ALTER TABLE ONLY player_parents
    ADD CONSTRAINT fk_player_parents_user FOREIGN KEY (user_id) REFERENCES users(id);

ALTER TABLE ONLY player_profiles
    ADD CONSTRAINT fk_player_profiles_user FOREIGN KEY (linked_user_id) REFERENCES users(id);

ALTER TABLE ONLY ride_offers
    ADD CONSTRAINT fk_ride_offers_driver FOREIGN KEY (driver_user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE ONLY ride_offers
    ADD CONSTRAINT fk_ride_offers_event FOREIGN KEY (event_id) REFERENCES team_events(id) ON DELETE CASCADE;

ALTER TABLE ONLY ride_seats
    ADD CONSTRAINT fk_ride_seats_booked_by FOREIGN KEY (booked_by_user_id) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE ONLY ride_seats
    ADD CONSTRAINT fk_ride_seats_event FOREIGN KEY (event_id) REFERENCES team_events(id) ON DELETE CASCADE;

ALTER TABLE ONLY ride_seats
    ADD CONSTRAINT fk_ride_seats_offer_event FOREIGN KEY (offer_id, event_id) REFERENCES ride_offers(id, event_id) ON DELETE CASCADE;

ALTER TABLE ONLY ride_seats
    ADD CONSTRAINT fk_ride_seats_profile FOREIGN KEY (player_profile_id) REFERENCES player_profiles(id) ON DELETE CASCADE;

ALTER TABLE ONLY team_events
    ADD CONSTRAINT fk_team_events_created_by FOREIGN KEY (created_by_user_id) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE ONLY team_events
    ADD CONSTRAINT fk_team_events_cup_match FOREIGN KEY (cup_match_id) REFERENCES cup_matches(id) ON DELETE SET NULL;

ALTER TABLE ONLY team_events
    ADD CONSTRAINT fk_team_events_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE;

ALTER TABLE ONLY team_events
    ADD CONSTRAINT fk_team_events_video_match FOREIGN KEY (video_match_id) REFERENCES matches(id) ON DELETE SET NULL;

ALTER TABLE ONLY team_invitations
    ADD CONSTRAINT fk_team_invitations_player_profile FOREIGN KEY (player_profile_id) REFERENCES player_profiles(id);

ALTER TABLE ONLY team_roster
    ADD CONSTRAINT fk_team_roster_profile FOREIGN KEY (player_profile_id) REFERENCES player_profiles(id);

ALTER TABLE ONLY team_roster
    ADD CONSTRAINT fk_team_roster_team FOREIGN KEY (team_id) REFERENCES teams(id);

ALTER TABLE ONLY match_squad_members
    ADD CONSTRAINT fkbw0m3srmgbtxyyl1rs4uolq8v FOREIGN KEY (team_id) REFERENCES teams(id);

ALTER TABLE ONLY matches
    ADD CONSTRAINT fkf6o79db22w12nv6xmq0fwgigs FOREIGN KEY (uploaded_by_user_id) REFERENCES users(id);

ALTER TABLE ONLY cups
    ADD CONSTRAINT fkgab5vsb5fruvda20mi1uyub70 FOREIGN KEY (created_by_id) REFERENCES users(id);

ALTER TABLE ONLY teams
    ADD CONSTRAINT fklt9rx2w5ob2w8t9y440yvxpxj FOREIGN KEY (club_id) REFERENCES clubs(id);

ALTER TABLE ONLY team_invitations
    ADD CONSTRAINT fkn2spna496q3cr3fmqc8gda2my FOREIGN KEY (team_id) REFERENCES teams(id);

ALTER TABLE ONLY cup_matches
    ADD CONSTRAINT fkqcy3hupnwahl5y2i8obo96o7y FOREIGN KEY (away_team_id) REFERENCES cup_teams(id);

ALTER TABLE ONLY clips
    ADD CONSTRAINT fkqnt6caldfkhqdotu2boem1ds9 FOREIGN KEY (match_id) REFERENCES matches(id);

ALTER TABLE ONLY coach_teams
    ADD CONSTRAINT fks78o19f8cfx8mb9nb9nw4dkww FOREIGN KEY (coach_id) REFERENCES users(id);

ALTER TABLE ONLY fan_favorite_teams
    ADD CONSTRAINT fktravn0x2hroaobe8s54xdedhu FOREIGN KEY (team_id) REFERENCES teams(id);

ALTER TABLE ONLY cup_teams
    ADD CONSTRAINT fkynkas69e2rfqc6ru15s24dt FOREIGN KEY (cup_id) REFERENCES cups(id);
