-- Match and clip files (raw video, HLS, tracking, field image, clip assets) still to be deleted from MinIO.
-- Written in the transaction that deletes the match, team or clip, and removed only after the objects are gone:
-- a rolled back delete leaves the files in place, a failed file delete is retried (StorageDeletionRetryJob).
-- The same idea as medical_file_deletions, for the other buckets. A prefix row deletes every object under it.
CREATE TABLE storage_deletions (
    id uuid NOT NULL,
    bucket character varying(100) NOT NULL,
    object_key character varying(1024) NOT NULL,
    is_prefix boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT storage_deletions_pkey PRIMARY KEY (id)
);
CREATE INDEX ix_storage_deletions_created_at ON storage_deletions (created_at);
