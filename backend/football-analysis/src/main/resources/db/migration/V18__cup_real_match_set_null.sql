-- A cup match may point at the analysed real match (cup_matches.real_match_id). Without an ON DELETE rule,
-- deleting that match, or a team with such a home match, failed on this foreign key. The cup result stays;
-- only the video link goes, the same as team_events.video_match_id.
ALTER TABLE cup_matches DROP CONSTRAINT fk8a2518b8vo1w2g0oc2pu6y39g;
ALTER TABLE cup_matches
    ADD CONSTRAINT fk_cup_matches_real_match FOREIGN KEY (real_match_id) REFERENCES matches(id) ON DELETE SET NULL;
