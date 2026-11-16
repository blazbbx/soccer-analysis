-- Every account is one User entity now. The single-table inheritance (Admin, Coach, Fan, ...) is gone,
-- so the discriminator column has no reader. (V17 drops users.role as well.)
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_user_type_check;
ALTER TABLE users DROP COLUMN user_type;
