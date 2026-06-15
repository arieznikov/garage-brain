ALTER TABLE import_jobs
    ADD COLUMN content_hash TEXT;

CREATE UNIQUE INDEX idx_import_jobs_vehicle_content_hash
    ON import_jobs (vehicle_id, content_hash)
    WHERE content_hash IS NOT NULL;
