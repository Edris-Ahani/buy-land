CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    full_name VARCHAR(160) NOT NULL,
    phone_number VARCHAR(13) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT app_users_phone_key UNIQUE (phone_number),
    CONSTRAINT app_users_phone_format CHECK (phone_number ~ '^\+989[0-9]{9}$'),
    CONSTRAINT app_users_full_name_not_blank CHECK (length(trim(full_name)) > 0)
);

CREATE TABLE auth_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX auth_sessions_user_idx ON auth_sessions (user_id);
CREATE INDEX auth_sessions_expiry_idx ON auth_sessions (expires_at);

CREATE TABLE auth_refresh_tokens (
    token_hash CHAR(64) PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES auth_sessions(id) ON DELETE CASCADE,
    consumed BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX auth_refresh_tokens_session_idx ON auth_refresh_tokens (session_id);
