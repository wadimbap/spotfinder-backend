create table auth_sessions
(
    id                  uuid primary key,
    user_id             uuid                     not null references users (id) on delete cascade,
    current_token_hash  varchar(64)              not null unique,
    created_at          timestamp with time zone not null,
    idle_expires_at     timestamp with time zone not null,
    absolute_expires_at timestamp with time zone not null,
    revoked_at          timestamp with time zone null,

    constraint auth_sessions_expiration_check
        check (idle_expires_at <= absolute_expires_at)
);

create index idx_auth_sessions_user_id on auth_sessions (user_id);
create index idx_auth_sessions_idle_expires_at on auth_sessions (idle_expires_at);

create table auth_refresh_tokens
(
    token_hash  varchar(64) primary key,
    session_id  uuid                     not null references auth_sessions (id) on delete cascade,
    request_id  uuid                     null,
    consumed_at timestamp with time zone null,

    constraint auth_refresh_tokens_consumption_check
        check ((request_id is null) = (consumed_at is null))
);

create index idx_auth_refresh_tokens_session_id on auth_refresh_tokens (session_id);