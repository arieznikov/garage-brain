CREATE TABLE vehicles (
    id UUID PRIMARY KEY,
    nickname TEXT NOT NULL,
    make TEXT,
    model TEXT,
    year INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE import_jobs (
    id UUID PRIMARY KEY,
    vehicle_id UUID REFERENCES vehicles (id),
    status TEXT NOT NULL,
    stage TEXT,
    error TEXT,
    parquet_path TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ
);

CREATE TABLE sessions (
    id UUID PRIMARY KEY,
    vehicle_id UUID NOT NULL REFERENCES vehicles (id),
    import_job_id UUID REFERENCES import_jobs (id),
    source TEXT NOT NULL,
    imported_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    drive_started_at TIMESTAMPTZ,
    drive_ended_at TIMESTAMPTZ,
    sample_count INT NOT NULL DEFAULT 0,
    parquet_path TEXT
);

CREATE TABLE session_aggregates (
    session_id UUID NOT NULL REFERENCES sessions (id),
    segment TEXT NOT NULL,
    pid_name TEXT NOT NULL,
    mean DOUBLE PRECISION,
    std DOUBLE PRECISION,
    min DOUBLE PRECISION,
    max DOUBLE PRECISION,
    n INT NOT NULL DEFAULT 0,
    PRIMARY KEY (session_id, segment, pid_name)
);

CREATE TABLE baselines (
    vehicle_id UUID NOT NULL REFERENCES vehicles (id),
    pid_name TEXT NOT NULL,
    mean DOUBLE PRECISION,
    std DOUBLE PRECISION,
    sample_count INT NOT NULL DEFAULT 0,
    session_count INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (vehicle_id, pid_name)
);

CREATE TABLE alerts (
    id UUID PRIMARY KEY,
    vehicle_id UUID NOT NULL REFERENCES vehicles (id),
    pid_name TEXT NOT NULL,
    metric TEXT NOT NULL,
    severity TEXT NOT NULL,
    z_score DOUBLE PRECISION,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    acknowledged BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_import_jobs_status ON import_jobs (status);
CREATE INDEX idx_sessions_vehicle ON sessions (vehicle_id, imported_at DESC);
