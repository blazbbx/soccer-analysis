-- The processing watchdog (ProcessingWatchdog) needs to know how long a match or a clip render has been
-- in its current state. created_at is not enough: a match can wait days for its corners before processing.
ALTER TABLE matches ADD COLUMN status_changed_at timestamp(6) without time zone;
UPDATE matches SET status_changed_at = created_at;

-- The Clip entity had this field as @Transient; now it is stored when the render is queued.
ALTER TABLE clips ADD COLUMN render_requested_at timestamp(6) without time zone;
UPDATE clips SET render_requested_at = created_at WHERE render_status IN ('QUEUED', 'PROCESSING');
