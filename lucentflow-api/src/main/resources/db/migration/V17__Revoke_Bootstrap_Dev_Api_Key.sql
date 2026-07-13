-- Harden multi-tenant bootstrap: revoke the well-known V13 default-dev-key.
-- Local/demo operators should use Admin API or demo_setup.sql instead of this credential.

UPDATE projects
SET is_active = FALSE,
    api_key = 'revoked-default-dev-key-' || id::text || '-' || floor(extract(epoch FROM clock_timestamp()))::bigint::text
WHERE api_key = 'default-dev-key';
