create table spot_photos (
                             id uuid primary key,
                             spot_id uuid not null,
                             object_key varchar(500) not null unique,
                             original_filename varchar(255) not null,
                             content_type varchar(100) not null,
                             size_bytes bigint not null,
                             created_by_user_id uuid not null,
                             created_at       timestamp with time zone not null,
                             updated_at       timestamp with time zone not null,

                             constraint fk_spot_photos_spot
                                 foreign key (spot_id)
                                     references spots (id)
                                     on delete cascade,

                             constraint fk_spot_photos_created_by_user
                                 foreign key (created_by_user_id)
                                     references users (id)
);

create index idx_spot_photos_spot_id
    on spot_photos (spot_id);

create index idx_spot_photos_created_by_user_id
    on spot_photos (created_by_user_id);