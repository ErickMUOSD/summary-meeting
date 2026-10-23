-- meeting-summary :: esquema inicial
-- Ejecutado automáticamente por el contenedor de Postgres en el primer arranque
-- (montado en /docker-entrypoint-initdb.d).

CREATE EXTENSION IF NOT EXISTS "pgcrypto"; -- gen_random_uuid()

-- ---------------------------------------------------------------------------
-- projects
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS projects (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- notes  (una nota pertenece a un proyecto y agrupa 1..N grabaciones)
--   status: created -> uploaded -> transcribed -> summarized
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notes (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id        UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    title             TEXT NOT NULL,
    status            TEXT NOT NULL DEFAULT 'created',
    combined_txt_path TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_notes_project ON notes(project_id);

-- ---------------------------------------------------------------------------
-- recordings  (nombre del archivo + texto simple transcrito por grabación)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS recordings (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    note_id           UUID NOT NULL REFERENCES notes(id) ON DELETE CASCADE,
    original_filename TEXT NOT NULL,
    stored_path       TEXT NOT NULL,
    mime              TEXT,
    duration_seconds  DOUBLE PRECISION,
    transcript_text   TEXT,
    status            TEXT NOT NULL DEFAULT 'uploaded',  -- uploaded|transcribing|done|error
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_recordings_note ON recordings(note_id);

-- ---------------------------------------------------------------------------
-- summaries  (texto del resumen de toda la nota, Markdown, con datos relevantes)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS summaries (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    note_id    UUID NOT NULL REFERENCES notes(id) ON DELETE CASCADE,
    content_md TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_summaries_note ON summaries(note_id);

-- ---------------------------------------------------------------------------
-- final_notes  (rutas de los entregables .md y .pdf)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS final_notes (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    note_id    UUID NOT NULL REFERENCES notes(id) ON DELETE CASCADE,
    md_path    TEXT,
    pdf_path   TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_final_notes_note ON final_notes(note_id);

-- ---------------------------------------------------------------------------
-- jobs  (cola de trabajo + soporte de polling/progreso)
--   type:   transcribe | summarize
--   status: pending | running | done | error
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS jobs (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    note_id      UUID NOT NULL REFERENCES notes(id) ON DELETE CASCADE,
    type         TEXT NOT NULL,
    status       TEXT NOT NULL DEFAULT 'pending',
    progress_pct INTEGER NOT NULL DEFAULT 0,
    stage        TEXT,
    error        TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_jobs_note   ON jobs(note_id);
CREATE INDEX IF NOT EXISTS idx_jobs_status ON jobs(status, created_at);
