-- Store project API keys as SHA-256 hashes (plaintext never at rest after this migration).
CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE projects ADD COLUMN IF NOT EXISTS api_key_hash VARCHAR(64);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS api_key_prefix VARCHAR(16);

UPDATE projects
SET
    api_key_hash = encode(digest(api_key, 'sha256'), 'hex'),
    api_key_prefix = left(api_key, LEAST(8, length(api_key)))
WHERE api_key_hash IS NULL
  AND api_key IS NOT NULL
  AND length(trim(api_key)) > 0;

-- Safety for any unexpected NULL rows after revoke/seed edge cases.
UPDATE projects
SET
    api_key_hash = encode(digest('orphan-project-' || id::text, 'sha256'), 'hex'),
    api_key_prefix = 'orphaned'
WHERE api_key_hash IS NULL;

ALTER TABLE projects ALTER COLUMN api_key_hash SET NOT NULL;
ALTER TABLE projects ALTER COLUMN api_key_prefix SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_projects_api_key_hash ON projects (api_key_hash);

ALTER TABLE projects DROP CONSTRAINT IF EXISTS projects_api_key_key;
ALTER TABLE projects DROP COLUMN IF EXISTS api_key;
