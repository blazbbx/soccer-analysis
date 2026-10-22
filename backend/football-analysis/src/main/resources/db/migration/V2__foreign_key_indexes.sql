-- Indexes for foreign keys that queries filter on, or that a delete has to check.

-- Already in V1. The dev database was switched to the baseline without them.
CREATE INDEX IF NOT EXISTS ix_event_attendances_profile ON event_attendances (player_profile_id);
CREATE INDEX IF NOT EXISTS ix_ride_seats_profile ON ride_seats (player_profile_id);

-- Invite list and cleanup by team, and cleanup by player profile.
CREATE INDEX IF NOT EXISTS ix_team_invitations_team ON team_invitations (team_id);
CREATE INDEX IF NOT EXISTS ix_team_invitations_profile ON team_invitations (player_profile_id);

-- Cup lists filter cups by the real teams in them. Deleting a team clears the link.
CREATE INDEX IF NOT EXISTS ix_cup_teams_real_team ON cup_teams (real_team_id);

-- Deleting a match or a cup match checks these references.
CREATE INDEX IF NOT EXISTS ix_cup_matches_real_match ON cup_matches (real_match_id);
CREATE INDEX IF NOT EXISTS ix_team_events_video_match ON team_events (video_match_id);
CREATE INDEX IF NOT EXISTS ix_team_events_cup_match ON team_events (cup_match_id);
CREATE INDEX IF NOT EXISTS ix_match_squad_members_match ON match_squad_members (match_id);
CREATE INDEX IF NOT EXISTS ix_match_squad_members_team ON match_squad_members (team_id);
CREATE INDEX IF NOT EXISTS ix_match_squad_members_player ON match_squad_members (player_id);

-- A driver's offers on a team, and cleanup when a user is deleted.
CREATE INDEX IF NOT EXISTS ix_ride_offers_driver ON ride_offers (driver_user_id);
CREATE INDEX IF NOT EXISTS ix_team_messages_sender ON team_messages (sender_id);
CREATE INDEX IF NOT EXISTS ix_clips_coach ON clips (coach_id);
