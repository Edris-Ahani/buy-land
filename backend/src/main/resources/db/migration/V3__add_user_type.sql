ALTER TABLE app_users
    ADD COLUMN user_type VARCHAR(16) NOT NULL DEFAULT 'user',
    ADD CONSTRAINT app_users_type_check CHECK (user_type IN ('user', 'admin'));
