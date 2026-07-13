-- Per-project webhook HMAC secret (plaintext; same trust model as global LUCENTFLOW_WEBHOOK_SECRET_TOKEN).
-- NULL means fall back to the global secret when signing outbound webhooks.
--
-- @author ArchLucent
-- @since 1.2
ALTER TABLE projects
    ADD COLUMN IF NOT EXISTS webhook_secret VARCHAR(256);
