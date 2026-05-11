create table spots
(
    id          uuid primary key,
    name        varchar(255) not null,
    description text,
    latitude    double precision not null,
    longitude   double precision not null,
    type        varchar(50) not null,
    created_at  timestamp with time zone,
    updated_at  timestamp with time zone
);

create table spot_features
(
    spot_id uuid not null,
    feature varchar(50) not null,

    constraint fk_spot_features_spot
        foreign key (spot_id)
            references spots (id)
            on delete cascade,

    constraint uk_spot_features_spot_feature
        unique (spot_id, feature)
);