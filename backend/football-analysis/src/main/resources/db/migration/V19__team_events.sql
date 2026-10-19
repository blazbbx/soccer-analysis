ALTER TABLE player_profiles ADD COLUMN IF NOT EXISTS can_offer_rides BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS team_events (
    id UUID PRIMARY KEY,
    team_id UUID NOT NULL,
    type VARCHAR(20) NOT NULL,
    starts_at TIMESTAMP NOT NULL,
    meeting_at TIMESTAMP NOT NULL,
    venue VARCHAR(255),
    meeting_point VARCHAR(255) NOT NULL,
    notes VARCHAR(2000),
    rsvp_deadline TIMESTAMP,
    video_match_id UUID,
    cup_match_id UUID,
    created_by_user_id UUID,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_team_events_team FOREIGN KEY (team_id) REFERENCES teams (id) ON DELETE CASCADE,
    CONSTRAINT fk_team_events_video_match FOREIGN KEY (video_match_id) REFERENCES matches (id) ON DELETE SET NULL,
    CONSTRAINT fk_team_events_cup_match FOREIGN KEY (cup_match_id) REFERENCES cup_matches (id) ON DELETE SET NULL,
    CONSTRAINT fk_team_events_created_by FOREIGN KEY (created_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_team_events_type CHECK (type IN ('MATCH', 'TRAINING', 'TOURNAMENT'))
);

CREATE INDEX IF NOT EXISTS ix_team_events_team_starts ON team_events (team_id, starts_at, id);

CREATE TABLE IF NOT EXISTS event_attendances (
    event_id UUID NOT NULL,
    player_profile_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    responded_by_user_id UUID,
    responded_at TIMESTAMP,
    comment VARCHAR(500),
    wants_ride BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (event_id, player_profile_id),
    CONSTRAINT fk_event_attendances_event FOREIGN KEY (event_id) REFERENCES team_events (id) ON DELETE CASCADE,
    CONSTRAINT fk_event_attendances_profile FOREIGN KEY (player_profile_id) REFERENCES player_profiles (id) ON DELETE CASCADE,
    CONSTRAINT fk_event_attendances_responded_by FOREIGN KEY (responded_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_event_attendances_status CHECK (status IN ('GOING', 'NOT_GOING', 'MAYBE', 'NO_RESPONSE'))
);

CREATE INDEX IF NOT EXISTS ix_event_attendances_profile ON event_attendances (player_profile_id);

CREATE TABLE IF NOT EXISTS ride_offers (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    driver_user_id UUID NOT NULL,
    seats_total INT NOT NULL,
    departure_place VARCHAR(255) NOT NULL,
    departure_at TIMESTAMP NOT NULL,
    notes VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_ride_offers_event FOREIGN KEY (event_id) REFERENCES team_events (id) ON DELETE CASCADE,
    CONSTRAINT fk_ride_offers_driver FOREIGN KEY (driver_user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_ride_offers_seats CHECK (seats_total BETWEEN 1 AND 8),
    CONSTRAINT ux_ride_offers_id_event UNIQUE (id, event_id),
    CONSTRAINT ux_ride_offers_event_driver UNIQUE (event_id, driver_user_id)
);

CREATE INDEX IF NOT EXISTS ix_ride_offers_event_id ON ride_offers (event_id);

CREATE TABLE IF NOT EXISTS ride_seats (
    id UUID PRIMARY KEY,
    offer_id UUID NOT NULL,
    event_id UUID NOT NULL,
    player_profile_id UUID NOT NULL,
    booked_by_user_id UUID,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_ride_seats_offer_event FOREIGN KEY (offer_id, event_id) REFERENCES ride_offers (id, event_id) ON DELETE CASCADE,
    CONSTRAINT fk_ride_seats_event FOREIGN KEY (event_id) REFERENCES team_events (id) ON DELETE CASCADE,
    CONSTRAINT fk_ride_seats_profile FOREIGN KEY (player_profile_id) REFERENCES player_profiles (id) ON DELETE CASCADE,
    CONSTRAINT fk_ride_seats_booked_by FOREIGN KEY (booked_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ux_ride_seats_event_profile UNIQUE (event_id, player_profile_id)
);

CREATE INDEX IF NOT EXISTS ix_ride_seats_offer_event ON ride_seats (offer_id, event_id);
CREATE INDEX IF NOT EXISTS ix_ride_seats_profile ON ride_seats (player_profile_id);
