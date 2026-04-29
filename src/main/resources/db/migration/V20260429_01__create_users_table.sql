create table users
(
    id               uuid primary key,
    email            varchar(255)             not null unique,
    password_hash    varchar(255)             not null,
    display_name     varchar(255)             not null,
    role             varchar(50)              not null,
    enabled          boolean                  not null default true,
    primary_activity varchar(50) null,
    created_at       timestamp with time zone not null,
    updated_at       timestamp with time zone not null
);

create index idx_users_email on users (email);
create index idx_users_role on users (role);