-- The roles of an account live only in Keycloak and reach the backend in the request's token.
-- users.role kept a copy of the strongest one from the last login; nothing reads it any more.
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users DROP COLUMN role;
